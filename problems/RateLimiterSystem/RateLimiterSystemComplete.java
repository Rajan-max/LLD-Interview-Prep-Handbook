package problems.RateLimiterSystem;

import java.util.*;

/**
 * RATE LIMITER SYSTEM - Complete Implementation
 *
 * Key Features:
 * - Strategy pattern: Limiter interface with multiple algorithm implementations
 * - Factory pattern: LimiterFactory creates correct Limiter from config
 * - Token Bucket: burst-friendly, smooth refill, O(1) per request
 * - Sliding Window Log: exact accuracy, timestamp tracking
 * - Per-client state tracking inside each algorithm
 * - No concurrency (single-threaded; discussed in extensibility)
 */

// ============================================================================
// RESULT VALUE OBJECT
// ============================================================================

class RateLimitResult {
    private final boolean allowed;
    private final int remaining;
    private final Long retryAfterMs; // null if allowed

    public RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfterMs = retryAfterMs;
    }

    public boolean isAllowed() { return allowed; }
    public int getRemaining() { return remaining; }
    public Long getRetryAfterMs() { return retryAfterMs; }

    @Override
    public String toString() {
        return allowed
                ? "ALLOWED (remaining=" + remaining + ")"
                : "DENIED (remaining=0, retryAfterMs=" + retryAfterMs + ")";
    }
}

// ============================================================================
// LIMITER INTERFACE (Strategy Pattern)
// ============================================================================

interface Limiter {
    RateLimitResult allow(String key);
}

// ============================================================================
// TOKEN BUCKET — Burst-friendly, smooth refill
// ============================================================================

class TokenBucketLimiter implements Limiter {
    private final int capacity;
    private final int refillRatePerSecond;
    private final Map<String, TokenBucket> buckets = new HashMap<>();

    public TokenBucketLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
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
            long retryAfterMs = (long) Math.ceil((tokensNeeded * 1000.0) / refillRatePerSecond);
            return new RateLimitResult(false, 0, retryAfterMs);
        }
    }

    static class TokenBucket {
        double tokens;
        long lastRefillTime;

        TokenBucket(double initialTokens, long time) {
            this.tokens = initialTokens;
            this.lastRefillTime = time;
        }
    }
}

// ============================================================================
// SLIDING WINDOW LOG — Exact accuracy, timestamp tracking
// ============================================================================

class SlidingWindowLogLimiter implements Limiter {
    private final int maxRequests;
    private final long windowMs;
    private final Map<String, Queue<Long>> logs = new HashMap<>();

    public SlidingWindowLogLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
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
            return new RateLimitResult(false, 0, Math.max(1, retryAfterMs));
        }
    }
}

// ============================================================================
// FACTORY (Factory Pattern)
// ============================================================================

class LimiterFactory {
    @SuppressWarnings("unchecked")
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
}

// ============================================================================
// RATE LIMITER — Orchestrator
// ============================================================================

class RateLimiter {
    private final Map<String, Limiter> limiters;
    private final Limiter defaultLimiter;

    public RateLimiter(List<Map<String, Object>> configs, Map<String, Object> defaultConfig) {
        this.limiters = new HashMap<>();
        LimiterFactory factory = new LimiterFactory();

        for (Map<String, Object> config : configs) {
            String endpoint = (String) config.get("endpoint");
            limiters.put(endpoint, factory.create(config));
        }

        this.defaultLimiter = factory.create(defaultConfig);
    }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    }
}

// ============================================================================
// DEMO AND TESTS
// ============================================================================

public class RateLimiterSystemComplete {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   RATE LIMITER SYSTEM — Strategy + Factory Demo          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        testTokenBucketBurst();
        testTokenBucketRefill();
        testTokenBucketRetryAfter();
        testSlidingWindowLog();
        testSlidingWindowLogExpiry();
        testRateLimiterOrchestrator();
        testDefaultFallback();
        testPerClientIsolation();
        testPerEndpointIsolation();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    // --- Test 1: Token Bucket allows burst up to capacity ---
    static void testTokenBucketBurst() {
        System.out.println("Test 1: Token Bucket — Burst Up To Capacity");
        System.out.println("-".repeat(56));

        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1);

        int allowed = 0;
        for (int i = 0; i < 7; i++) {
            RateLimitResult r = limiter.allow("user1");
            if (r.isAllowed()) allowed++;
        }

        check("5 of 7 rapid requests allowed (capacity=5)", allowed == 5);
        check("6th request denied", !limiter.allow("user1").isAllowed());
        System.out.println();
    }

    // --- Test 2: Token Bucket refills over time ---
    static void testTokenBucketRefill() throws Exception {
        System.out.println("Test 2: Token Bucket — Refill Over Time");
        System.out.println("-".repeat(56));

        TokenBucketLimiter limiter = new TokenBucketLimiter(3, 10); // 10 tokens/sec

        // Exhaust all tokens
        for (int i = 0; i < 3; i++) limiter.allow("user1");
        check("Exhausted: next request denied", !limiter.allow("user1").isAllowed());

        // Wait for refill (100ms = 1 token at 10/sec)
        Thread.sleep(150);
        RateLimitResult r = limiter.allow("user1");
        check("After 150ms refill: request allowed", r.isAllowed());
        System.out.println();
    }

    // --- Test 3: Token Bucket retryAfterMs accuracy ---
    static void testTokenBucketRetryAfter() {
        System.out.println("Test 3: Token Bucket — retryAfterMs Accuracy");
        System.out.println("-".repeat(56));

        TokenBucketLimiter limiter = new TokenBucketLimiter(1, 2); // 2 tokens/sec

        limiter.allow("user1"); // consume the 1 token
        RateLimitResult denied = limiter.allow("user1");

        check("Denied after exhaustion", !denied.isAllowed());
        check("retryAfterMs is positive", denied.getRetryAfterMs() != null && denied.getRetryAfterMs() > 0);
        check("retryAfterMs <= 500ms (need 1 token at 2/sec)", denied.getRetryAfterMs() <= 500);
        System.out.println();
    }

    // --- Test 4: Sliding Window Log basic ---
    static void testSlidingWindowLog() {
        System.out.println("Test 4: Sliding Window Log — Basic Limit");
        System.out.println("-".repeat(56));

        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(3, 1000); // 3 req per 1s

        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            if (limiter.allow("user1").isAllowed()) allowed++;
        }

        check("3 of 5 requests allowed (maxRequests=3)", allowed == 3);
        RateLimitResult denied = limiter.allow("user1");
        check("4th+ request denied", !denied.isAllowed());
        check("retryAfterMs is positive", denied.getRetryAfterMs() != null && denied.getRetryAfterMs() > 0);
        System.out.println();
    }

    // --- Test 5: Sliding Window Log expiry ---
    static void testSlidingWindowLogExpiry() throws Exception {
        System.out.println("Test 5: Sliding Window Log — Window Expiry");
        System.out.println("-".repeat(56));

        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(2, 200); // 2 req per 200ms

        limiter.allow("user1");
        limiter.allow("user1");
        check("Exhausted: denied", !limiter.allow("user1").isAllowed());

        Thread.sleep(250); // wait for window to expire
        RateLimitResult r = limiter.allow("user1");
        check("After window expiry: allowed again", r.isAllowed());
        check("Remaining = 1 after 1 request in new window", r.getRemaining() == 1);
        System.out.println();
    }

    // --- Test 6: RateLimiter orchestrator with mixed algorithms ---
    static void testRateLimiterOrchestrator() {
        System.out.println("Test 6: RateLimiter — Mixed Algorithms Per Endpoint");
        System.out.println("-".repeat(56));

        List<Map<String, Object>> configs = List.of(
                Map.of("endpoint", "/search",
                        "algorithm", "TokenBucket",
                        "algoConfig", Map.of("capacity", 3, "refillRatePerSecond", 1)),
                Map.of("endpoint", "/upload",
                        "algorithm", "SlidingWindowLog",
                        "algoConfig", Map.of("maxRequests", 2, "windowMs", 1000L))
        );

        Map<String, Object> defaultConfig = Map.of(
                "algorithm", "TokenBucket",
                "algoConfig", Map.of("capacity", 10, "refillRatePerSecond", 5));

        RateLimiter rl = new RateLimiter(configs, defaultConfig);

        // /search uses TokenBucket(3, 1)
        int searchAllowed = 0;
        for (int i = 0; i < 5; i++) {
            if (rl.allow("client1", "/search").isAllowed()) searchAllowed++;
        }
        check("/search: 3 of 5 allowed (TokenBucket capacity=3)", searchAllowed == 3);

        // /upload uses SlidingWindowLog(2, 1000)
        int uploadAllowed = 0;
        for (int i = 0; i < 4; i++) {
            if (rl.allow("client1", "/upload").isAllowed()) uploadAllowed++;
        }
        check("/upload: 2 of 4 allowed (SlidingWindowLog max=2)", uploadAllowed == 2);
        System.out.println();
    }

    // --- Test 7: Default fallback for unknown endpoint ---
    static void testDefaultFallback() {
        System.out.println("Test 7: Default Fallback for Unknown Endpoint");
        System.out.println("-".repeat(56));

        List<Map<String, Object>> configs = List.of(
                Map.of("endpoint", "/search",
                        "algorithm", "TokenBucket",
                        "algoConfig", Map.of("capacity", 2, "refillRatePerSecond", 1))
        );

        Map<String, Object> defaultConfig = Map.of(
                "algorithm", "TokenBucket",
                "algoConfig", Map.of("capacity", 5, "refillRatePerSecond", 1));

        RateLimiter rl = new RateLimiter(configs, defaultConfig);

        // /unknown uses default (capacity=5)
        int allowed = 0;
        for (int i = 0; i < 7; i++) {
            if (rl.allow("client1", "/unknown").isAllowed()) allowed++;
        }
        check("/unknown falls back to default: 5 of 7 allowed", allowed == 5);
        System.out.println();
    }

    // --- Test 8: Per-client isolation ---
    static void testPerClientIsolation() {
        System.out.println("Test 8: Per-Client Isolation");
        System.out.println("-".repeat(56));

        TokenBucketLimiter limiter = new TokenBucketLimiter(2, 1);

        // Exhaust client A
        limiter.allow("clientA");
        limiter.allow("clientA");
        check("Client A exhausted", !limiter.allow("clientA").isAllowed());

        // Client B should be unaffected
        check("Client B still allowed", limiter.allow("clientB").isAllowed());
        check("Client B 2nd request allowed", limiter.allow("clientB").isAllowed());
        check("Client B exhausted after 2", !limiter.allow("clientB").isAllowed());
        System.out.println();
    }

    // --- Test 9: Per-endpoint isolation ---
    static void testPerEndpointIsolation() {
        System.out.println("Test 9: Per-Endpoint Isolation");
        System.out.println("-".repeat(56));

        List<Map<String, Object>> configs = List.of(
                Map.of("endpoint", "/api1",
                        "algorithm", "TokenBucket",
                        "algoConfig", Map.of("capacity", 1, "refillRatePerSecond", 1)),
                Map.of("endpoint", "/api2",
                        "algorithm", "TokenBucket",
                        "algoConfig", Map.of("capacity", 1, "refillRatePerSecond", 1))
        );

        Map<String, Object> defaultConfig = Map.of(
                "algorithm", "TokenBucket",
                "algoConfig", Map.of("capacity", 10, "refillRatePerSecond", 1));

        RateLimiter rl = new RateLimiter(configs, defaultConfig);

        // Exhaust /api1 for client1
        rl.allow("client1", "/api1");
        check("/api1 exhausted for client1", !rl.allow("client1", "/api1").isAllowed());

        // Same client on /api2 should be independent
        check("/api2 still allowed for client1", rl.allow("client1", "/api2").isAllowed());
        System.out.println();
    }

    // --- Helper ---
    static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  ✓ " + name);
            passed++;
        } else {
            System.out.println("  ✗ FAIL: " + name);
            failed++;
        }
    }
}
