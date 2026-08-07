# IronBook — Low-Latency Matching Engine

A single-node, low-latency stock exchange matching engine built from scratch in Java 21. Inspired by the [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/) architecture used by real financial exchanges to process millions of orders per second.

This is not a toy project — it implements the same core design principles that power Wall Street's fastest trading systems: a **single-threaded sequencer**, **price-time priority matching**, a **write-ahead log** for crash recovery, and a **TCP networking layer** for real client connections.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        TCP CLIENTS                                   │
│           ┌──────────┐ ┌──────────┐ ┌──────────┐                     │
│           │ Client 1 │ │ Client 2 │ │ Client N │                     │
│           └────┬─────┘ └────┬─────┘ └────┬─────┘                     │
│                │            │            │                           │
│ ═══════════════╪════════════╪════════════╪══════════════════════════ │
│                ▼            ▼            ▼                           │
│           ┌──────────────────────────────────┐                       │
│           │         TCP SERVER               │                       │
│           │   (Thread Pool - 10 workers)     │                       │
│           │                                  │                       │
│           │  Each client gets its own thread  │                      │
│           │  Worker parses raw text into      │                      │
│           │  EngineCommands, drops them       │                      │
│           │  into the queue, and goes back    │                      │
│           │  to listening immediately.        │                      │
│           └──────────────┬───────────────────┘                       │
│                          │                                           │
│                          ▼                                           │
│           ┌──────────────────────────────────┐                       │
│           │    LinkedBlockingQueue (FIFO)     │                      │
│           │                                  │                       │
│           │  Thread-safe inbox. Multiple      │                      │
│           │  producers (TCP threads), single  │                      │
│           │  consumer (sequencer). Strict     │                      │
│           │  first-in-first-out ordering.     │                      │
│           └──────────────┬───────────────────┘                       │
│                          │                                           │
│                          ▼                                           │
│           ┌──────────────────────────────────┐                       │
│           │    SEQUENCER THREAD (single)      │                      │
│           │                                  │                       │
│           │  The ONLY thread that touches     │                      │
│           │  the OrderBook or WAL. Pulls      │                      │
│           │  commands one-at-a-time:          │                      │
│           │                                  │                       │
│           │  1. Write to log (durability)     │                      │
│           │  2. Match in OrderBook (logic)    │                      │
│           │  3. Next command                  │                      │
│           └──────────┬───────┬───────────────┘                       │
│                      │       │                                       │
│              ┌───────┘       └───────┐                               │
│              ▼                       ▼                               │
│  ┌─────────────────────┐  ┌─────────────────────┐                    │
│  │   Write-Ahead Log   │  │     Order Book       │                   │
│  │                     │  │                      │                   │
│  │  Append-only file.  │  │  Bids: TreeMap       │                   │
│  │  Every event is     │  │    (highest first)   │                   │
│  │  flushed to disk    │  │                      │                   │
│  │  BEFORE the book    │  │  Asks: TreeMap       │                   │ 
│  │  is touched. This   │  │    (lowest first)    │                   │
│  │  is what survives   │  │                      │                   │
│  │  a crash.           │  │  Index: HashMap      │                   │
│  │                     │  │    (O(1) cancel)     │                   │
│  └─────────────────────┘  └──────────────────────┘                   │
│                                                                      │
│  On restart, LogReplayer reads the log top-to-bottom and replays     │
│  every event into a fresh OrderBook. Because matching is             │
│  deterministic, the rebuilt state is identical to what existed       │
│  before the crash.                                                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## How It Works

### 1. Order Matching — Price-Time Priority

When a new order arrives, the engine checks if it can immediately trade against an existing order on the opposite side:

| Incoming Order | Matches Against | Condition |
|---|---|---|
| **BUY** at $150 | Resting **SELL** orders | If the best (lowest) ask price ≤ $150 |
| **SELL** at $100 | Resting **BUY** orders | If the best (highest) bid price ≥ $100 |

If multiple resting orders exist at the same price, the one that arrived **first** gets filled first. This is called **time priority** — it's the same fairness rule used by NYSE and NASDAQ.

If only part of an order can be filled (e.g., you want 10 shares but only 4 are available), the order is **partially filled** and the remaining 6 shares rest in the book, waiting for a future match.

### 2. The Sequencer — Why Single-Threaded is Faster

The most counterintuitive part of this engine: **only one thread ever touches the OrderBook.** 

When 10 clients connect simultaneously, their TCP threads don't try to modify the book. Instead, each thread writes a lightweight command object and drops it into a shared queue. A single dedicated "sequencer" thread pulls from this queue and processes commands one at a time.

**Why is one thread faster than ten?**
- **Zero lock contention.** With multiple threads, every access to shared data requires locks, which block and slow everything down. With one thread, no locks are needed at all.
- **CPU cache friendliness.** One thread keeps the book's data hot in L1/L2 cache. Multiple threads cause constant cache invalidation.
- **Determinism for free.** The queue imposes a strict order. Log order = processing order = replay order. Crash recovery works automatically.

This is the exact principle behind the [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/), which processes 6 million orders per second on a single thread.

### 3. Crash Recovery — Write-Ahead Logging

Every order is written to a log file on disk **before** it is processed in memory. If the engine crashes:

1. On restart, `LogReplayer` reads the entire log file top-to-bottom.
2. It replays every `NEW` order and `CANCEL` event into a fresh, empty `OrderBook`.
3. Because matching is deterministic (same inputs → same outputs), the rebuilt state is **identical** to what existed before the crash.
4. The sequence counter is seeded past the highest historical value, so new orders never reuse old IDs.

No data is lost. No manual intervention required.

### 4. TCP Networking

Clients connect via raw TCP sockets and send plain-text commands:

```
NEW,BUY,150,10       →  Buy 10 shares at price 150
NEW,SELL,100,5        →  Sell 5 shares at price 100
CANCEL,O-42           →  Cancel order with ID "O-42"
```

Each client gets its own thread from a fixed pool of 10 workers. The `OrderMessageParser` validates and converts raw text into structured commands before they reach the engine.

---

## Project Structure

```
src/main/java/com/ironbook/matching_engine/
├── MatchingEngine.java          # Central orchestrator — queue, sequencer, WAL, OrderBook
├── EngineCommand.java           # Sealed interface: NewOrderCommand | CancelCommand
├── MatchingEngineApplication.java
├── LoadGenerator.java           # Standalone Fake Trader Bot for benchmarking
│
├── Model/
│   ├── Order.java               # Immutable-ish order (price in long, not double)
│   ├── Trade.java               # Record of a completed match
│   ├── Side.java                # Enum: BUY | SELL
│   └── OrderStatus.java         # Enum: NEW | PARTIALLY_FILLED | FILLED | CANCELLED
│
├── Book/
│   └── OrderBook.java           # Dual TreeMap + HashMap — matching + O(1) cancel
│
├── Log/
│   ├── WriteAheadLog.java       # Append-only, flush-on-write durability
│   └── LogReplayer.java         # Deterministic replay for crash recovery
│
└── Network/
    ├── TCPServer.java           # Socket listener with thread pool
    └── OrderMessageParser.java  # Text protocol parser (NEW/CANCEL)
```

---

## Data Structures & Complexity

| Operation | Data Structure | Time Complexity |
|---|---|---|
| Best bid/ask lookup | `TreeMap.firstEntry()` | O(log n) |
| Add order at price level | `TreeMap.computeIfAbsent()` + `LinkedList.add()` | O(log n) |
| Cancel by orderId | `HashMap.remove()` + `LinkedList.remove()` | O(1) lookup + O(k) removal within level |
| Match incoming order | Walk `TreeMap` + drain `LinkedList` | O(m) where m = number of fills |

The `OrderBook` uses plain (non-concurrent) collections (`TreeMap`, `LinkedList`, `HashMap`) because the sequencer guarantees only one thread ever accesses them. This is faster than `ConcurrentSkipListMap`/`ConcurrentHashMap` because it avoids all internal synchronization overhead.

---

## Test Suite

**16 tests across 4 test classes**, covering unit, integration, and stress testing:

| Test Class | Tests | What It Proves |
|---|---|---|
| `OrderBookTest` | 8 | Matching logic: full fills, partial fills, price-time priority, cancellation, no-cross scenarios |
| `CrashRecoveryTest` | 4 | WAL durability: orders survive crashes, partial fills recover correctly, cancels persist, sequence numbers don't collide |
| `TCPServerTest` | 2 | End-to-end integration: real TCP socket → parser → engine → OrderBook, verified with `CountDownLatch` (zero `Thread.sleep`) |
| `ConcurrencyStressTest` | 2 | Thread safety: 10 threads × 1,000 orders = 10,000 orders processed with zero shares lost or duplicated |
| `LoadGeneratorTest` | 1 | Benchmarking integration: full end-to-end multi-client load generation over raw TCP |

Run all tests:

```bash
./mvnw test -Dtest="OrderBookTest,CrashRecoveryTest,TCPServerTest,ConcurrencyStressTest,LoadGeneratorTest"
```

---

## Key Design Decisions

| Decision | Why |
|---|---|
| **`long` for price, not `double`** | Floating-point math causes rounding errors in money. Using `long` (cents/paise) is how real exchanges avoid `0.1 + 0.2 ≠ 0.3` bugs. |
| **Sealed interface for commands** | Java 21's `sealed interface` + `record` types give compile-time exhaustiveness checks — the `switch` in `executeCommand()` will fail to compile if a new command type is added but not handled. |
| **`CountDownLatch` for server readiness** | Tests use `server.awaitReady()` instead of `Thread.sleep()`. The latch unblocks the exact millisecond the socket is bound — no guessing, no flaky tests. |
| **Plain collections over concurrent ones** | Since the sequencer guarantees single-threaded access to the OrderBook, `TreeMap` is faster than `ConcurrentSkipListMap` (no CAS overhead). |
| **WAL flushes on every write** | `writer.flush()` after every `append()` trades throughput for durability. In a matching engine, losing a confirmed order is worse than being slightly slower. |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven (with Maven Wrapper) |
| Testing | JUnit 5 |
| Networking | Java `ServerSocket` / `Socket` (raw TCP) |
| Concurrency | `LinkedBlockingQueue`, `CountDownLatch`, `AtomicLong` |
| Collections | `TreeMap`, `LinkedList`, `HashMap` |

---

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+ (or use the included Maven Wrapper)

### Run

```bash
# Clone the repository
git clone https://github.com/haardiksagar/IornBook-Matching-Engine.git
cd IornBook-Matching-Engine

# Build and run tests
./mvnw test

# Run the application
./mvnw spring-boot:run
```

### Connect a client (using telnet or netcat)

```bash
telnet localhost 9999
# Then type:
NEW,BUY,150,10
NEW,SELL,150,5
CANCEL,O-1
```

---

## Stress Test Results

```
Threads: 10
Orders per thread: 1,000
Total orders: 10,000
Shares per side: 25,000
Resting bids after matching: 0
Resting asks after matching: 0
→ Zero shares lost, zero shares duplicated ✅
```

---

## Load Generator (Fake Trader Bot)

A standalone benchmarking tool that simulates multiple trading clients connecting over real TCP sockets and firing random orders at the engine.

### Quick Start

```bash
# Terminal 1: Start the engine + TCP server
./mvnw compile exec:java -Dexec.mainClass="com.ironbook.matching_engine.MatchingEngineApplication"

# Terminal 2: Run the load generator (5 clients × 1,000 orders at full speed)
./mvnw compile exec:java -Dexec.mainClass="com.ironbook.matching_engine.LoadGenerator"
```

### CLI Options

```
Usage: LoadGenerator [options]

Options:
  --host <hostname>    Server host (default: localhost)
  --port <port>        Server port (default: 9999)
  --clients <n>        Number of simultaneous TCP clients (default: 5)
  --orders <n>         Orders per client (default: 1000)
  --rate <n>           Orders per second per client, 0 = unlimited (default: 0)
  --help               Show this help message

Examples:
  LoadGenerator                              # 5 clients × 1000 orders, full speed
  LoadGenerator --clients 10 --orders 5000   # 10 clients × 5000 orders
  LoadGenerator --rate 100                   # 5 clients, throttled to 100 orders/sec each
```

### Sample Benchmark Output

```
=== IronBook Load Generator ===

Connecting 5 clients to localhost:9999...
Each client will send 1000 random orders.
Rate limit: UNLIMITED (full throttle)
Total orders: 5000

All clients connected. Firing!
  Progress: 1000 / 5000 orders sent...
  Progress: 2000 / 5000 orders sent...
  Progress: 3000 / 5000 orders sent...
  Client 0 finished (1000 orders).
  Progress: 4000 / 5000 orders sent...
  Client 4 finished (1000 orders).
  Client 2 finished (1000 orders).
  Client 3 finished (1000 orders).
  Progress: 5000 / 5000 orders sent...
  Client 1 finished (1000 orders).

========== BENCHMARK RESULTS ==========
  Total orders sent:  5000
  Total time:         104 ms
  Throughput:         48076 orders/sec
  Avg latency:        20.8 µs/order
=======================================
```

---

## Future Work

- [ ] FIX protocol support (industry-standard messaging)
- [ ] WebSocket API for real-time market data streaming
- [ ] Metrics dashboard (throughput, latency percentiles)
- [ ] Multi-symbol support (separate order book per ticker)

