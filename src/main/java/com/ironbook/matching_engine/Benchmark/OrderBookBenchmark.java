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


}