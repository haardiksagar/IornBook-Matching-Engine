package com.ironbook.matching_engine.Benchmark;

import com.ironbook.matching_engine.MatchingEngine;
import com.ironbook.matching_engine.Model.Side;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * TICKET-18: Concurrent benchmark of the FULL MatchingEngine pipeline.
 *
 * Unlike OrderBookBenchmark (TICKET-17) which tests the naked OrderBook
 * in single-threaded isolation, this benchmark tests what actually
 * happens in production:
 *
 *   Multiple threads → LinkedBlockingQueue → Sequencer → WAL → OrderBook
 *
 * This measures the REAL throughput ceiling of the system, including:
 * - Queue insertion contention (multiple producers pushing simultaneously)
 * - Sequencer bottleneck (single consumer pulling one-at-a-time)
 * - WAL disk I/O (flush on every write)
 *
 * The comparison between TICKET-17 and TICKET-18 answers the key question:
 * "How much overhead does the sequencer pattern add compared to raw matching?"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)                  // ONE shared MatchingEngine for all threads
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
// Thread count is NOT hardcoded here — it's set per-run in main()
// so we can compare 1, 2, 4, and 8 threads in a single benchmark session.
public class ConcurrentEngineBenchmark {

    private MatchingEngine engine;
    private File tempLogFile;

    // Each thread gets its own Random to avoid lock contention
    // on a shared Random (which would serialize our parallel threads
    // and make the benchmark measure Random's internal lock, not our engine)
    @State(Scope.Thread)
    public static class ThreadState {
        Random random = new Random();
    }

    private static final long MIN_PRICE = 95L;
    private static final long MAX_PRICE = 105L;

    /**
     * Creates a fresh MatchingEngine with a temporary WAL file.
     * This runs once before the entire benchmark starts.
     * The temp file is deleted on tearDown so we don't litter
     * the filesystem with thousands of benchmark log files.
     */
    @Setup(Level.Trial)
    public void setUp() throws IOException {
        tempLogFile = File.createTempFile("jmh-benchmark-", ".log");
        tempLogFile.deleteOnExit();
        engine = new MatchingEngine(tempLogFile.getAbsolutePath());
    }

    /**
     * Cleanly shuts down the engine and deletes the temp WAL file.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        engine.shutdown();
        if (tempLogFile != null) {
            tempLogFile.delete();
        }
    }

    /**
     * The core benchmark: submit a random order through the FULL pipeline.
     *
     * submitNewOrder() is fire-and-forget — it builds an EngineCommand,
     * drops it into the LinkedBlockingQueue, and returns immediately.
     * The sequencer thread picks it up later and does the actual matching.
     *
     * What we're measuring here is the PRODUCER side: how fast can
     * 4 threads push orders into the queue? The sequencer drains
     * them as fast as it can on the other end.
     */
    @Benchmark
    public void submitRandomOrder_throughput(ThreadState threadState) {
        Side side = threadState.random.nextBoolean() ? Side.BUY : Side.SELL;
        long price = MIN_PRICE + threadState.random.nextInt(11);
        int quantity = 1 + threadState.random.nextInt(20);

        engine.submitNewOrder(side, price, quantity);
    }

    /**
     * Same operation, but measured as AVERAGE TIME instead of throughput.
     * This tells us: "How long does a single submitNewOrder() call take
     * on average?" — reported in microseconds.
     *
     * Since submitNewOrder() is fire-and-forget (it just enqueues),
     * this measures queue insertion latency under contention, not
     * the full matching latency.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void submitRandomOrder_latency(ThreadState threadState) {
        Side side = threadState.random.nextBoolean() ? Side.BUY : Side.SELL;
        long price = MIN_PRICE + threadState.random.nextInt(11);
        int quantity = 1 + threadState.random.nextInt(20);

        engine.submitNewOrder(side, price, quantity);
    }

    /**
     * Runs the benchmark at multiple thread counts (1, 2, 4, 8)
     * to produce a scaling comparison table.
     *
     * For clean results from the jar:
     *   java -jar target/benchmarks.jar ConcurrentEngineBenchmark -t 4
     *
     * From IDE, this main() automatically iterates all thread counts.
     */
    public static void main(String[] args) throws RunnerException {
        int[] threadCounts = {1, 2, 4, 8};

        for (int threads : threadCounts) {
            System.out.println("\n>>> Running with " + threads + " thread(s)...\n");

            Options opt = new OptionsBuilder()
                    .include(ConcurrentEngineBenchmark.class.getSimpleName())
                    .forks(1)
                    .threads(threads)
                    .warmupIterations(3)
                    .measurementIterations(5)
                    .build();

            new Runner(opt).run();
        }
    }
}
