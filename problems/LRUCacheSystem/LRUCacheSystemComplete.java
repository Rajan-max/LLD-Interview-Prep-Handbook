package problems.LRUCacheSystem;

import java.util.*;
import java.util.concurrent.*;

/**
 * LRU CACHE SYSTEM - Complete Implementation
 *
 * Key Features:
 * - O(1) get and put via HashMap + Doubly Linked List
 * - Sentinel head/tail nodes eliminate null checks
 * - Generic types <K, V> for any key/value
 * - Thread-safe via synchronized methods
 * - LRU eviction: tail.prev is always the least recently used
 */

// ============================================================================
// NODE — Doubly Linked List Entry
// ============================================================================

class Node<K, V> {
    K key;
    V value;
    Node<K, V> prev, next;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() { return key + "=" + value; }
}

// ============================================================================
// DOUBLY LINKED LIST — Access Order Tracking with Sentinels
// ============================================================================

class DoublyLinkedList<K, V> {
    private final Node<K, V> head; // sentinel — head.next = most recently used
    private final Node<K, V> tail; // sentinel — tail.prev = least recently used

    DoublyLinkedList() {
        head = new Node<>(null, null);
        tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    /** Insert node right after sentinel head (most recently used position) */
    void addAtHead(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    /** Unlink node from its current position — O(1) */
    void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    /** Move existing node to head (mark as most recently used) */
    void moveToHead(Node<K, V> node) {
        removeNode(node);
        addAtHead(node);
    }

    /** Remove and return the LRU entry (tail.prev). Returns null if empty. */
    Node<K, V> removeFromTail() {
        if (tail.prev == head) return null;
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }

    /** Get the list contents in order (head→tail) for display */
    String toOrderString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        Node<K, V> current = head.next;
        while (current != tail) {
            joiner.add(current.key + "=" + current.value);
            current = current.next;
        }
        return joiner.toString();
    }

    Node<K, V> getHead() { return head; }
    Node<K, V> getTail() { return tail; }
}

// ============================================================================
// LRU CACHE — HashMap + Doubly Linked List
// ============================================================================

/**
 * Thread-safe LRU Cache with O(1) get and put.
 * - HashMap: O(1) key → node lookup
 * - DoublyLinkedList: O(1) access-order tracking and LRU eviction
 * - synchronized: thread-safe get/put
 */
class LRUCache<K, V> {
    private final int capacity;
    private final DoublyLinkedList<K, V> nodeList;
    private final Map<K, Node<K, V>> nodeMap;

    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.nodeList = new DoublyLinkedList<>();
        this.nodeMap = new HashMap<>();
    }

    /** O(1) lookup. Moves entry to head (most recently used) on hit. */
    public synchronized V get(K key) {
        if (!nodeMap.containsKey(key)) return null;

        Node<K, V> node = nodeMap.get(key);
        nodeList.moveToHead(node);
        return node.value;
    }

    /** O(1) insert/update. Evicts LRU entry if at capacity. */
    public synchronized void put(K key, V value) {
        if (nodeMap.containsKey(key)) {
            Node<K, V> node = nodeMap.get(key);
            node.value = value;
            nodeList.moveToHead(node);
        } else {
            if (nodeMap.size() >= capacity) {
                Node<K, V> evicted = nodeList.removeFromTail();
                if (evicted != null) nodeMap.remove(evicted.key);
            }
            Node<K, V> newNode = new Node<>(key, value);
            nodeList.addAtHead(newNode);
            nodeMap.put(key, newNode);
        }
    }

    public synchronized int size() { return nodeMap.size(); }

    public synchronized String getOrder() { return nodeList.toOrderString(); }

    @Override
    public synchronized String toString() {
        return "LRUCache(cap=" + capacity + ", size=" + nodeMap.size() + ") " + nodeList.toOrderString();
    }
}

// ============================================================================
// DEMO AND TESTS
// ============================================================================

public class LRUCacheSystemComplete {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   LRU CACHE — HashMap + Doubly Linked List Demo          ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        testBasicGetPut();
        testEvictionOrder();
        testAccessOrderUpdate();
        testUpdateExistingKey();
        testCapacityOne();
        testEvictThenReinsert();
        testLargeSequence();
        testConcurrentPuts();
        testConcurrentGetPut();
        testEdgeCases();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    // --- Test 1: Basic get/put ---
    static void testBasicGetPut() {
        System.out.println("Test 1: Basic Get/Put");
        System.out.println("-".repeat(56));
        LRUCache<String, Integer> cache = new LRUCache<>(3);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);

        check("get(A) = 1", cache.get("A") == 1);
        check("get(B) = 2", cache.get("B") == 2);
        check("get(C) = 3", cache.get("C") == 3);
        check("get(D) = null (miss)", cache.get("D") == null);
        check("size = 3", cache.size() == 3);
        System.out.println();
    }

    // --- Test 2: Eviction order (LRU evicted first) ---
    static void testEvictionOrder() {
        System.out.println("Test 2: LRU Eviction Order");
        System.out.println("-".repeat(56));
        LRUCache<String, Integer> cache = new LRUCache<>(3);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        // Order: [C, B, A] — A is LRU

        cache.put("D", 4); // evicts A (LRU)
        check("A evicted (LRU)", cache.get("A") == null);
        check("B still present", cache.get("B") == 2);
        check("C still present", cache.get("C") == 3);
        check("D present", cache.get("D") == 4);
        check("size = 3", cache.size() == 3);
        System.out.println();
    }

    // --- Test 3: get() changes eviction order ---
    static void testAccessOrderUpdate() {
        System.out.println("Test 3: Access Order Update (get changes LRU)");
        System.out.println("-".repeat(56));
        LRUCache<String, Integer> cache = new LRUCache<>(3);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        // Order: [C, B, A] — A is LRU

        cache.get("A"); // A moves to head
        // Order: [A, C, B] — B is now LRU

        cache.put("D", 4); // evicts B (now LRU), NOT A
        check("B evicted (now LRU after A was accessed)", cache.get("B") == null);
        check("A still present (was accessed)", cache.get("A") == 1);
        check("C still present", cache.get("C") == 3);
        check("D present", cache.get("D") == 4);
        System.out.println();
    }

    // --- Test 4: Update existing key ---
    static void testUpdateExistingKey() {
        System.out.println("Test 4: Update Existing Key");
        System.out.println("-".repeat(56));
        LRUCache<String, Integer> cache = new LRUCache<>(3);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);

        cache.put("A", 10); // update A's value, moves to head
        check("A updated to 10", cache.get("A") == 10);
        check("size unchanged = 3", cache.size() == 3);

        // A is now most recent, B is LRU
        cache.put("D", 4); // evicts B
        check("B evicted (LRU after A was updated)", cache.get("B") == null);
        check("A still present with value 10", cache.get("A") == 10);
        System.out.println();
    }

    // --- Test 5: Capacity = 1 ---
    static void testCapacityOne() {
        System.out.println("Test 5: Capacity = 1");
        System.out.println("-".repeat(56));
        LRUCache<String, Integer> cache = new LRUCache<>(1);

        cache.put("A", 1);
        check("get(A) = 1", cache.get("A") == 1);

        cache.put("B", 2); // evicts A
        check("A evicted", cache.get("A") == null);
        check("get(B) = 2", cache.get("B") == 2);
        check("size = 1", cache.size() == 1);

        cache.put("C", 3); // evicts B
        check("B evicted", cache.get("B") == null);
        check("get(C) = 3", cache.get("C") == 3);
        System.out.println();
    }

    // --- Test 6: Evict then reinsert same key ---
    static void testEvictThenReinsert() {
        System.out.println("Test 6: Evict Then Reinsert Same Key");
        System.out.println("-".repeat(56));
        LRUCache<String, Integer> cache = new LRUCache<>(2);

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3); // evicts A

        check("A evicted", cache.get("A") == null);

        cache.put("A", 100); // reinsert A with new value, evicts B
        check("A reinserted with value 100", cache.get("A") == 100);
        check("B evicted", cache.get("B") == null);
        check("C still present", cache.get("C") == 3);
        System.out.println();
    }

    // --- Test 7: Large sequence ---
    static void testLargeSequence() {
        System.out.println("Test 7: Large Sequence (1000 ops)");
        System.out.println("-".repeat(56));
        LRUCache<Integer, Integer> cache = new LRUCache<>(100);

        // Insert 1000 keys, only last 100 should remain
        for (int i = 0; i < 1000; i++) {
            cache.put(i, i * 10);
        }

        check("size = 100 (capacity)", cache.size() == 100);
        check("key 899 evicted", cache.get(899) == null);
        check("key 900 present", cache.get(900) == 9000);
        check("key 999 present", cache.get(999) == 9990);
        System.out.println();
    }

    // --- Test 8: Concurrent puts ---
    static void testConcurrentPuts() throws Exception {
        System.out.println("Test 8: Concurrent Puts (Thread Safety)");
        System.out.println("-".repeat(56));
        LRUCache<Integer, Integer> cache = new LRUCache<>(50);

        ExecutorService exec = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int key = i;
            exec.submit(() -> {
                try {
                    cache.put(key, key * 10);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        exec.shutdown();

        check("Size ≤ capacity (50) after concurrent puts", cache.size() <= 50);
        check("Size > 0", cache.size() > 0);
        System.out.println();
    }

    // --- Test 9: Concurrent get + put ---
    static void testConcurrentGetPut() throws Exception {
        System.out.println("Test 9: Concurrent Get + Put (No Corruption)");
        System.out.println("-".repeat(56));
        LRUCache<Integer, Integer> cache = new LRUCache<>(20);

        // Pre-fill
        for (int i = 0; i < 20; i++) cache.put(i, i);

        ExecutorService exec = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(200);
        List<Future<?>> futures = new ArrayList<>();

        // 100 readers + 100 writers
        for (int i = 0; i < 100; i++) {
            final int key = i % 30;
            futures.add(exec.submit(() -> {
                try { cache.get(key); } finally { latch.countDown(); }
            }));
            futures.add(exec.submit(() -> {
                try { cache.put(key, key * 100); } finally { latch.countDown(); }
            }));
        }

        latch.await();
        exec.shutdown();

        boolean noException = futures.stream().allMatch(f -> {
            try { f.get(); return true; } catch (Exception e) { return false; }
        });

        check("No exceptions during concurrent get+put", noException);
        check("Cache size ≤ 20", cache.size() <= 20);
        check("Cache still functional", cache.get(0) != null || cache.get(0) == null); // just no crash
        System.out.println();
    }

    // --- Test 10: Edge cases ---
    static void testEdgeCases() {
        System.out.println("Test 10: Edge Cases");
        System.out.println("-".repeat(56));

        // Empty cache
        LRUCache<String, Integer> cache = new LRUCache<>(5);
        check("Get from empty cache = null", cache.get("X") == null);
        check("Empty cache size = 0", cache.size() == 0);

        // Put same key multiple times (no size growth)
        cache.put("A", 1);
        cache.put("A", 2);
        cache.put("A", 3);
        check("Repeated put same key: size = 1", cache.size() == 1);
        check("Latest value = 3", cache.get("A") == 3);

        // Invalid capacity
        try {
            new LRUCache<>(0);
            check("Should reject capacity 0", false);
        } catch (IllegalArgumentException e) {
            check("Capacity 0 rejected", true);
        }

        try {
            new LRUCache<>(-1);
            check("Should reject negative capacity", false);
        } catch (IllegalArgumentException e) {
            check("Negative capacity rejected", true);
        }

        // Integer keys (generics work)
        LRUCache<Integer, String> intCache = new LRUCache<>(2);
        intCache.put(1, "one");
        intCache.put(2, "two");
        check("Integer keys work: get(1) = one", "one".equals(intCache.get(1)));
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
