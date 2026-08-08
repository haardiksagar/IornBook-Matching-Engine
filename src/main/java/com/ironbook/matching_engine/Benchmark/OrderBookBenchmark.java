package com.ironbook.matching_engine.Benchmark;

import com.ironbook.matching_engine.Book.OrderBook;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TICKET-17: Single-threaded baseline benchmark.
 *
 * Tests the OrderBook's matching logic in complete isolation:
 * - no network, no WAL, no TCP server
 * - single thread only - this is the theoretical speed ceiling
 *   your concurrent version can never exceed
 *
 * Run via: java -jar target/benchmarks.jar OrderBookBenchmark
 * Or directly from IntelliJ by running main() below.
 */
@BenchmarkMode(Mode.Throughput)          // measure: how many ops/sec?
@OutputTimeUnit(TimeUnit.SECONDS)        // report in: ops/second
@State(Scope.Benchmark)                  // one shared state instance for the whole benchmark run
@Warmup(iterations = 3, time = 2)       // run 3 warmup rounds of 2 seconds each - discarded
@Measurement(iterations = 5, time = 3)  // then 5 real measurement rounds of 3 seconds each
@Fork(1)                                 // run in a fresh JVM process - eliminates JVM state pollution
public class OrderBookBenchmark {

    private OrderBook orderBook;
    private AtomicLong sequenceCounter;
    private Random random;

    // Price range: orders clustered between 95 and 105
    // so roughly half of them will cross and match,
    // and half will rest in the book - realistic mix
    private static final long MIN_PRICE = 95L;
    private static final long MAX_PRICE = 105L;

    /**
     * @Setup runs once before all benchmark iterations start.
     * This is NOT measured - only the @Benchmark method is timed.
     * We pre-seed the book with some resting orders on both sides
     * so the benchmark measures a realistic "live book" scenario,
     * not an always-empty book (which would only ever rest, never match).
     */
    @Setup(Level.Trial)
    public void setUp() {
        orderBook = new OrderBook();
        sequenceCounter = new AtomicLong(0);
        random = new Random(42); // fixed seed = reproducible random sequence

        // pre-seed 100 resting sell orders at various prices
        for (int i = 0; i < 100; i++) {
            long price = MIN_PRICE + (i % 11); // spread across 95-105
            Order sell = makeOrder(Side.SELL, price, 10);
            orderBook.addOrder(sell);
        }

        // pre-seed 100 resting buy orders at various prices
        for (int i = 0; i < 100; i++) {
            long price = MIN_PRICE + (i % 11);
            Order buy = makeOrder(Side.BUY, price, 10);
            orderBook.addOrder(buy);
        }
    }

}