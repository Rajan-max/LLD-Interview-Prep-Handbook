# Rate Limiter System - LLD Interview Solution 🚦
---

## 1) Requirements (~5 min)

**Prompt**: "Build an in-memory rate limiter for an API gateway. The system receives configuration specifying rate limiting rules per endpoint."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What info comes with each request? | clientId (string) + endpoint (string) |
| **Primary capabilities** | What should we return? | Structured result: allowed, remaining quota, retryAfterMs |
| **Rules** | Can different endpoints use different algorithms? | Yes — config specifies algorithm + algorithm-specific params |
| **Rules** | What if endpoint has no config? | Fall back to a default configuration |
| **Error handling** | Unknown algorithm in config? | Fail fast with error |
| **Scope** | Distributed or single-process? | Single-process, in-memory |
| **Scope** | Dynamic config updates? | No — loaded once at startup |
| **Scope** | Thread safety? | Start single-threaded, discuss concurrency as extension |

### Requirements

```
1. Configuration loaded at startup (endpoint → algorithm + params)
2. Requests arrive as (clientId, endpoint) pairs
3. Each endpoint has its own algorithm (e.g., TokenBucket, SlidingWindowLog)
4. Per-client rate limiting — each client tracked independently
5. Return structured result: (allowed, remaining, retryAfterMs)
6. Unknown endpoints fall back to default config

Out of Scope:
- Distributed rate limiting (Redis, coordination)
- Dynamic configuration updates
- Metrics and monitoring
- Thread safety (discussed in extensibility)
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- RateLimiter       (orchestrator — receives requests, delegates to correct limiter)
- Limiter           (interface — contract for all rate limiting algorithms)
- TokenBucketLimiter    (impl — allows bursts, smooth refill)
- SlidingWindowLogLimiter (impl — exact timestamp tracking, perfect accuracy)
- LimiterFactory    (creates correct Limiter from config data)
- RateLimitResult   (value object — allowed, remaining, retryAfterMs)

NOT entities (just primitives/params):
- Request           (just clientId + endpoint strings — method params, not a class)
- Client            (just a string key — no state or behavior of its own)
- Endpoint          (just a string key for config lookup)

Relationships:
- RateLimiter → Limiter (1:N via Map<endpoint, Limiter>)
- RateLimiter → LimiterFactory (uses at startup to create limiters)
- Each Limiter tracks per-client state internally (Map<clientId, state>)
- Limiter.allow(clientId) → RateLimitResult
```

**Key decisions:**
- Strategy pattern: Limiter interface with multiple algorithm implementations
- Factory pattern: LimiterFactory creates the right Limiter from heterogeneous config
- Per-client state lives inside each Limiter (not in RateLimiter) — each algorithm tracks state differently
- Eager instantiation at startup — all limiters created in constructor, not lazily

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Each endpoint has its own algorithm" | Map<endpoint, Limiter> | RateLimiter |
| "Unknown endpoints fall back to default" | defaultLimiter | RateLimiter |
| "Per-client rate limiting" | Map<clientId, bucket/log> | Inside each Limiter impl |
| "TokenBucket: capacity + refill rate" | tokens, lastRefillTime per client | TokenBucketLimiter |
| "SlidingWindowLog: max requests in window" | Queue<timestamp> per client | SlidingWindowLogLimiter |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Check if request is allowed | allow(clientId, endpoint) → RateLimitResult | RateLimiter |
| Algorithm-specific rate check | allow(key) → RateLimitResult | Limiter (interface) |
| Create limiter from config | create(config) → Limiter | LimiterFactory |

### Class Outlines

```
class RateLimitResult:                      // Immutable value object
  - allowed: boolean
  - remaining: int
  - retryAfterMs: Long (nullable)

interface Limiter:
  + allow(key) → RateLimitResult

class TokenBucketLimiter implements Limiter:
  - capacity: int
  - refillRatePerSecond: int
  - buckets: Map<String, TokenBucket>       // per-client state

  + allow(key) → RateLimitResult

  TokenBucket (inner):
    - tokens: double
    - lastRefillTime: long

class SlidingWindowLogLimiter implements Limiter:
  - maxRequests: int
  - windowMs: long
  - logs: Map<String, Queue<Long>>          // per-client timestamp log

  + allow(key) → RateLimitResult

class LimiterFactory:
  + create(config) → Limiter                // switch on algorithm name

class RateLimiter:                          // Orchestrator
  - limiters: Map<String, Limiter>
  - defaultLimiter: Limiter

  + RateLimiter(configs, defaultConfig)
  + allow(clientId, endpoint) → RateLimitResult
```

### Key Principle

- **RateLimiter** is a thin orchestrator — lookup endpoint → delegate to Limiter
- **Limiter** implementations own all algorithm logic AND per-client state
- **LimiterFactory** isolates creation logic — adding a new algorithm = new class + one factory case

### Algorithm Comparison

| Algorithm | Per-Client State | Tradeoff |
|---|---|---|
| **Token Bucket** | (tokens, lastRefillTime) | Allows bursts, smooth refill, O(1) per request |
| **Sliding Window Log** | Queue\<timestamp\> | Perfect accuracy, higher memory O(n) |

---

## 4) Concurrency Control (~5 min)

**The initial implementation is single-threaded** (per interviewer's guidance). However, the concurrency discussion is important for extensibility.

```
Current: Single-threaded, no locks needed.

If concurrent requests were required:
- Shared resource: per-client state (TokenBucket, timestamp log)
- Race condition: two threads read tokens=1, both allow, both decrement → over-limit
- Solution: per-key locking (synchronized on the bucket/log object)
  - ConcurrentHashMap for the buckets/logs map
  - computeIfAbsent for atomic get-or-create
  - synchronized(bucket) for the check-and-update operation
  - Different clients don't block each other (only same-client requests contend)
```

**Mention this briefly in the interview** — "starting single-threaded per requirements, but the design supports per-key locking if we need concurrency later."

---

## 5) Implementation (~10 min)

### Core Method: RateLimiter.allow() — Thin Orchestrator

```java
public RateLimitResult allow(String clientId, String endpoint) {
    Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
    return limiter.allow(clientId);
}
```

That's the entire orchestrator. Lookup + delegate.

### Core Method: TokenBucketLimiter.allow() — Burst-Friendly Algorithm

```java
public RateLimitResult allow(String key) {
    TokenBucket bucket = buckets.computeIfAbsent(key,
            k -> new TokenBucket(capacity, System.currentTimeMillis()));

    long now = System.currentTimeMillis();
    double elapsed = now - bucket.lastRefillTime;
    double tokensToAdd = (elapsed * refillRatePerSecond) / 1000.0;
    bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
    bucket.lastRefillTime = now;

    if (bucket.tokens >= 1) {
        bucket.tokens -= 1;
        return new RateLimitResult(true, (int) bucket.tokens, null);
    } else {
        double tokensNeeded = 1 - bucket.tokens;
        long retryAfterMs = (long) Math.ceil((tokensNeeded * 1000) / refillRatePerSecond);
        return new RateLimitResult(false, 0, retryAfterMs);
    }
}
```

**What this demonstrates:**
- Lazy refill: no background thread — tokens refilled on-demand based on elapsed time
- New clients start with full capacity (burst-friendly)
- retryAfterMs calculated precisely: how long until enough tokens accumulate
- `double` for tokens because refill is continuous (0.5 tokens is valid)

### Core Method: SlidingWindowLogLimiter.allow() — Exact Accuracy

```java
public RateLimitResult allow(String key) {
    Queue<Long> log = logs.computeIfAbsent(key, k -> new LinkedList<>());

    long now = System.currentTimeMillis();
    long cutoff = now - windowMs;

    // Evict stale timestamps
    while (!log.isEmpty() && log.peek() < cutoff) {
        log.poll();
    }

    if (log.size() < maxRequests) {
        log.add(now);
        return new RateLimitResult(true, maxRequests - log.size(), null);
    } else {
        long oldestTimestamp = log.peek();
        long retryAfterMs = (oldestTimestamp + windowMs) - now;
        return new RateLimitResult(false, 0, retryAfterMs);
    }
}
```

**What this demonstrates:**
- Exact tracking: every request timestamp stored in a FIFO queue
- Stale eviction: remove timestamps outside the window before counting
- retryAfterMs: when the oldest timestamp ages out, one slot opens up

### Core Method: LimiterFactory.create() — Factory Pattern

```java
public Limiter create(Map<String, Object> config) {
    String algorithm = (String) config.get("algorithm");
    Map<String, Object> algoConfig = (Map<String, Object>) config.get("algoConfig");

    return switch (algorithm) {
        case "TokenBucket" -> new TokenBucketLimiter(
                (int) algoConfig.get("capacity"),
                (int) algoConfig.get("refillRatePerSecond"));
        case "SlidingWindowLog" -> new SlidingWindowLogLimiter(
                (int) algoConfig.get("maxRequests"),
                ((Number) algoConfig.get("windowMs")).longValue());
        default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
    };
}
```

### Verification: Walk Through a Scenario

```
Setup: TokenBucket for "/search" — capacity=5, refillRate=2/sec
Client "user1" makes rapid requests:

Request 1 (t=0ms):
  → bucket created: tokens=5, lastRefill=0
  → elapsed=0, refill=0, tokens=5
  → tokens >= 1 → allow, consume → tokens=4
  → Result: (allowed=true, remaining=4, retryAfterMs=null)

Request 2-5 (t=10ms each, rapid fire):
  → minimal refill each time (~0.02 tokens)
  → tokens: 4→3→2→1→0.08
  → All allowed, remaining decreases

Request 6 (t=50ms):
  → elapsed=10ms, refill=0.02, tokens=0.10
  → tokens < 1 → DENIED
  → tokensNeeded = 1 - 0.10 = 0.90
  → retryAfterMs = ceil(0.90 * 1000 / 2) = 450ms
  → Result: (allowed=false, remaining=0, retryAfterMs=450)

Request 7 (t=600ms — after waiting):
  → elapsed=550ms, refill=1.1, tokens=min(5, 0.10+1.1)=1.2
  → tokens >= 1 → allow, consume → tokens=0.2
  → Result: (allowed=true, remaining=0, retryAfterMs=null)

✓ Burst of 5 allowed, then denied, then allowed after refill.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- TokenBucket: 5 rapid requests with capacity=5 → all allowed, 6th denied
- TokenBucket: wait for refill → requests allowed again
- SlidingWindowLog: maxRequests=3 in 1000ms → 3 allowed, 4th denied, wait 1s → allowed again
- Unknown endpoint → falls back to default limiter
- retryAfterMs is accurate (wait that long, next request succeeds)

**Algorithm-specific tests:**
- TokenBucket burst: capacity=10, send 10 instantly → all pass, 11th fails
- TokenBucket refill: verify partial refill (0.5 tokens after half the refill interval)
- SlidingWindowLog sliding: requests at t=0, t=500, t=1000 with window=1000ms → at t=1001 the first request ages out

**Edge cases:**
- First request from a new client → always allowed (fresh bucket/empty log)
- Two different clients on same endpoint → independent limits
- Same client on different endpoints → independent limits (different Limiter instances)
- retryAfterMs = 0 edge case (exactly at boundary)

---

## 7) Extensibility (~5 min)

**"How would you add a new rate limiting algorithm (e.g., Fixed Window Counter)?"**
> "Implement the Limiter interface with the new algorithm's logic. Add one case to LimiterFactory. RateLimiter doesn't change. Existing algorithms don't change. This is the Strategy + Factory pattern paying off."

```java
class FixedWindowCounterLimiter implements Limiter {
    // ... tracks (count, windowStart) per client
}

// In LimiterFactory.create():
case "FixedWindowCounter" -> new FixedWindowCounterLimiter(
        (int) algoConfig.get("maxRequests"),
        ((Number) algoConfig.get("windowMs")).longValue());
```

**"How would you handle thread safety for concurrent requests?"**
> "Per-key locking. Use ConcurrentHashMap for the buckets/logs map. `computeIfAbsent` for atomic get-or-create. `synchronized(bucket)` for the check-and-update. Different clients don't block each other — only same-client requests contend."

**"How would you handle memory growth from millions of clients?"**
> "Add an eviction policy. Track lastAccessTime per key. A background ScheduledExecutorService scans periodically and removes entries idle for > 1 hour. Alternatively, use an LRU cache with fixed capacity — evict least recently used client state when full."

**"How would you support dynamic config updates?"**
> "Two approaches: (1) Simple: `reloadConfig()` that rebuilds all limiters from scratch — loses per-client state but simple. (2) Stateful: add `updateConfig()` to Limiter interface — each algorithm adjusts params while preserving per-client state. Option 2 is better for production but more complex."

**"How would you make this distributed across multiple servers?"**
> "Replace in-memory maps with Redis. Token Bucket: use Redis INCR with TTL. Sliding Window Log: use Redis sorted sets with ZRANGEBYSCORE. The Limiter interface stays the same — just swap implementations. RateLimiter doesn't change."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **RateLimiter.allow** | O(1) lookup + algorithm cost | O(1) |
| **TokenBucket.allow** | O(1) | O(1) per client |
| **SlidingWindowLog.allow** | O(k) eviction, k = stale entries | O(n) per client, n = requests in window |
| **LimiterFactory.create** | O(1) | O(1) |
| **Total space** | | O(C × E) clients × endpoints |

---

**Implementation**: See [RateLimiterSystemComplete.java](./RateLimiterSystemComplete.java)
