# LRU Cache System - LLD Interview Solution 🗄️
---

## 1) Requirements (~5 min)

**Prompt**: "Design a Least Recently Used (LRU) cache."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | `get(key)` and `put(key, value)` |
| **Rules** | What happens on cache miss? | Return null |
| **Rules** | What happens when cache is full? | Evict the least recently used entry |
| **Rules** | Does `get` count as "use"? | Yes — moves entry to most recently used |
| **Rules** | Does `put` on existing key update it? | Yes — updates value and marks as most recently used |
| **Error handling** | Null keys? | Not supported (simplifies design) |
| **Scope** | Thread safety? | Yes — concurrent get/put from multiple threads |
| **Scope** | Generic types? | Yes — `LRUCache<K, V>` supports any key/value types |

### Requirements

```
1. Fixed-capacity cache initialized at construction time
2. get(key) → returns value if present (cache hit), null if absent (cache miss)
3. put(key, value) → inserts or updates entry
4. On cache hit (get or put-update): entry becomes most recently used
5. On capacity overflow: evict least recently used entry before inserting new one
6. Both get and put must be O(1) time complexity
7. Thread-safe under concurrent access

Out of Scope:
- TTL / expiration-based eviction
- Cache statistics (hit rate, miss rate)
- Distributed caching
- Persistence
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Node<K, V>           (doubly-linked list node — key, value, prev, next pointers)
- DoublyLinkedList<K, V> (maintains access order — head = most recent, tail = least recent)
- LRUCache<K, V>       (orchestrator — HashMap for O(1) lookup + DLL for O(1) eviction)

NOT entities:
- Eviction policy       (LRU is the only policy — no Strategy pattern needed)

Relationships:
- LRUCache → HashMap<K, Node<K,V>> (O(1) key lookup to find the node)
- LRUCache → DoublyLinkedList<K,V> (O(1) move-to-head, O(1) remove-from-tail)
- DoublyLinkedList → Node (sentinel head ↔ nodes ↔ sentinel tail)
```

**Key decisions:**
- HashMap + Doubly Linked List = O(1) for both get and put
- HashMap alone can't track access order. DLL alone can't do O(1) lookup. Together they solve both.
- Sentinel head/tail nodes eliminate null checks in DLL operations
- `synchronized` on get/put for thread safety (simple, correct for interview scope)

### Why This Data Structure Combination?

| Approach | get | put | evict | Problem |
|---|---|---|---|---|
| HashMap only | O(1) | O(1) | O(n) | Can't find LRU in O(1) |
| LinkedList only | O(n) | O(n) | O(1) | Can't lookup by key in O(1) |
| **HashMap + DLL** | **O(1)** | **O(1)** | **O(1)** | **✅ Both O(1)** |

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Fixed capacity" | capacity: int | LRUCache |
| "O(1) key lookup" | nodeMap: HashMap<K, Node<K,V>> | LRUCache |
| "O(1) access-order tracking" | nodeList: DoublyLinkedList<K,V> | LRUCache |
| "Each entry has key + value" | key: K, value: V | Node |
| "Doubly linked for O(1) remove" | prev, next: Node | Node |
| "Head = most recent, tail = LRU" | sentinel head, sentinel tail | DoublyLinkedList |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Lookup by key | get(key) → V | LRUCache |
| Insert or update | put(key, value) | LRUCache |
| Mark as most recently used | moveToHead(node) | DoublyLinkedList |
| Evict least recently used | removeFromTail() → Node | DoublyLinkedList |
| Add new entry at head | addAtHead(node) | DoublyLinkedList |
| Remove a specific node | removeNode(node) | DoublyLinkedList |

### Class Outlines

```
class Node<K, V>:
  - key: K
  - value: V
  - prev, next: Node<K, V>

class DoublyLinkedList<K, V>:
  - head: Node (sentinel)                   // head.next = most recently used
  - tail: Node (sentinel)                   // tail.prev = least recently used

  + addAtHead(node)                         // O(1) — insert after sentinel head
  + removeNode(node)                        // O(1) — unlink from current position
  + moveToHead(node)                        // O(1) — removeNode + addAtHead
  + removeFromTail() → Node                 // O(1) — remove tail.prev (LRU entry)

class LRUCache<K, V>:
  - capacity: int
  - nodeMap: HashMap<K, Node<K, V>>         // O(1) lookup
  - nodeList: DoublyLinkedList<K, V>        // O(1) order tracking

  + get(key) → V                            // synchronized
  + put(key, value)                         // synchronized
```

### Key Principle — Sentinel Nodes

```
Sentinel Head ↔ Node A ↔ Node B ↔ Node C ↔ Sentinel Tail
(most recent)                              (least recent / LRU)
```

- Sentinel head and tail are dummy nodes that never hold real data
- They eliminate null checks: `head.next` is always valid, `tail.prev` is always valid
- Empty cache: `head.next == tail` and `tail.prev == head`
- This simplifies every DLL operation — no special cases for first/last node

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- `nodeMap` (HashMap) — concurrent get/put from multiple threads
- `nodeList` (DoublyLinkedList) — concurrent moveToHead/removeFromTail

**What can go wrong?**
- Lost update: Thread A reads node, Thread B evicts it, Thread A uses stale pointer
- Corrupted DLL: Two threads moveToHead simultaneously → broken prev/next pointers
- Inconsistent state: nodeMap and nodeList out of sync (node in map but not in list)

**What's the locking strategy?**
- Method-level `synchronized` on get and put. Simple, correct, sufficient for interview.

### Concurrency Strategy

```
Locking approach:
- synchronized on get() and put() — coarse-grained but correct
- Ensures atomicity of check-then-act patterns:
  - get: check map → move to head (must be atomic)
  - put: check map → update or evict+insert (must be atomic)

Thread-safety:
- Node: mutable (value, prev, next) — protected by LRUCache's synchronized methods
- DoublyLinkedList: not independently thread-safe — protected by LRUCache
- HashMap: not independently thread-safe — protected by LRUCache
- LRUCache: thread-safe via synchronized methods

Why not ConcurrentHashMap + fine-grained locking?
- ConcurrentHashMap alone doesn't protect the DLL operations
- The map and list MUST be updated atomically (they're coupled)
- Fine-grained locking (e.g., striped locks) is complex and error-prone
- For interview scope, synchronized is the right choice
```

**Production alternative (discussed in extensibility):**
- Read-write lock: multiple concurrent reads, exclusive writes
- Striped locking: partition keys into segments, lock per segment
- Lock-free: ConcurrentLinkedDeque + ConcurrentHashMap (complex, rarely needed)

---

## 5) Implementation (~10 min)

### Core Data Structure: Node

```java
class Node<K, V> {
    K key;
    V value;
    Node<K, V> prev, next;

    Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
```

### Core Data Structure: DoublyLinkedList with Sentinels

```java
class DoublyLinkedList<K, V> {
    private final Node<K, V> head;  // sentinel
    private final Node<K, V> tail;  // sentinel

    DoublyLinkedList() {
        head = new Node<>(null, null);
        tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    void addAtHead(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    void moveToHead(Node<K, V> node) {
        removeNode(node);
        addAtHead(node);
    }

    Node<K, V> removeFromTail() {
        if (tail.prev == head) return null;  // empty
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
}
```

**What this demonstrates:**
- Sentinel nodes: no null checks anywhere — `head.next` and `tail.prev` always valid
- addAtHead: 4 pointer updates, O(1)
- removeNode: 2 pointer updates, O(1) — works for any node in the list
- moveToHead: remove + add = O(1)
- removeFromTail: removes `tail.prev` (the LRU entry), O(1)

### Core Method: LRUCache.get — O(1) Lookup + Move to Head

```java
public synchronized V get(K key) {
    if (!nodeMap.containsKey(key)) return null;  // cache miss

    Node<K, V> node = nodeMap.get(key);
    nodeList.moveToHead(node);                   // mark as most recently used
    return node.value;
}
```

### Core Method: LRUCache.put — O(1) Insert/Update + Eviction

```java
public synchronized void put(K key, V value) {
    if (nodeMap.containsKey(key)) {
        // UPDATE: change value, move to head
        Node<K, V> node = nodeMap.get(key);
        node.value = value;
        nodeList.moveToHead(node);
    } else {
        // INSERT: evict LRU if full, then add new entry
        if (nodeMap.size() >= capacity) {
            Node<K, V> evicted = nodeList.removeFromTail();
            if (evicted != null) nodeMap.remove(evicted.key);
        }
        Node<K, V> newNode = new Node<>(key, value);
        nodeList.addAtHead(newNode);
        nodeMap.put(key, newNode);
    }
}
```

**What this demonstrates:**
- Check-then-act is atomic (synchronized)
- Update path: change value + moveToHead (no eviction needed)
- Insert path: evict LRU if at capacity, then add at head
- Eviction removes from BOTH the DLL (removeFromTail) and the HashMap (nodeMap.remove)
- New entry added to BOTH the DLL (addAtHead) and the HashMap (nodeMap.put)

### Verification: Walk Through a Scenario

```
LRUCache(capacity=3)

put("A", 1):  List: [A=1]                    Map: {A→nodeA}
put("B", 2):  List: [B=2, A=1]               Map: {A→nodeA, B→nodeB}
put("C", 3):  List: [C=3, B=2, A=1]          Map: {A→nodeA, B→nodeB, C→nodeC}

get("A"):     List: [A=1, C=3, B=2]          Map: unchanged
              → A moved to head (most recent). B is now LRU.

put("D", 4):  Cache full! Evict LRU (B=2)
              List: [D=4, A=1, C=3]          Map: {A→nodeA, C→nodeC, D→nodeD}
              → B evicted from both list and map. D added at head.

get("B"):     → returns null (evicted)

put("A", 10): Update existing key
              List: [A=10, D=4, C=3]         Map: {A→nodeA(10), C→nodeC, D→nodeD}
              → A's value updated to 10, moved to head. C is now LRU.

put("E", 5):  Cache full! Evict LRU (C=3)
              List: [E=5, A=10, D=4]         Map: {A→nodeA, D→nodeD, E→nodeE}

✓ O(1) for every operation. LRU eviction correct. Access order maintained.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Basic get/put: insert 3 items, get each → correct values
- Cache miss: get non-existent key → null
- Eviction: insert beyond capacity → LRU evicted
- Access order: get moves entry to most recent, changes eviction order
- Update existing key: put same key with new value → value updated, moved to head
- Evict-then-reinsert: evicted key can be re-added

**Concurrency tests:**
- **Parallel puts**: 10 threads inserting different keys → cache size ≤ capacity
- **Parallel get+put**: readers and writers simultaneously → no corruption, no exceptions
- **Eviction under contention**: many threads inserting → evictions happen correctly

**Edge cases:**
- Capacity = 1: every put evicts the previous entry
- Put same key repeatedly: value updates, no size growth
- Get after eviction: returns null
- Empty cache: get returns null

---

## 7) Extensibility (~5 min)

**"How would you add TTL (time-to-live) expiration?"**
> "Add a `createdAt` or `expiresAt` timestamp to each Node. On `get`, check if the node is expired — if so, remove it and return null. Optionally, a background thread periodically scans and evicts expired entries."

**"How would you improve concurrency (read-write lock)?"**
> "Replace `synchronized` with a `ReentrantReadWriteLock`. `get` acquires the read lock (multiple concurrent reads). `put` acquires the write lock (exclusive). Caveat: `get` also modifies the list (moveToHead), so it actually needs the write lock too — unless we use a separate 'access order' update that's deferred."

**"How would you add eviction callbacks?"**
> "Observer pattern. Add an `EvictionListener<K, V>` interface with `onEvict(key, value)`. LRUCache fires the callback when evicting. Useful for writing evicted entries to disk or logging."

```java
interface EvictionListener<K, V> {
    void onEvict(K key, V value);
}
```

**"How would you support different eviction policies (LFU, FIFO)?"**
> "Extract the eviction logic into an `EvictionPolicy<K, V>` interface. LRU, LFU, FIFO become implementations. The cache delegates `onAccess` and `evict` to the policy. Strategy pattern."

**"How would you make this distributed?"**
> "Use consistent hashing to partition keys across cache nodes. Each node runs its own LRUCache. For cache coherence, use write-through or write-behind to a shared store (Redis, Memcached). The LRUCache class itself doesn't change."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **get(key)** | O(1) | O(1) |
| **put(key, value)** | O(1) | O(1) |
| **eviction** | O(1) | O(1) |
| **Total space** | | O(capacity) |

*HashMap lookup = O(1). DLL addAtHead/removeNode/removeFromTail = O(1). No operation iterates.*

---

**Implementation**: See [LRUCacheSystemComplete.java](./LRUCacheSystemComplete.java)
