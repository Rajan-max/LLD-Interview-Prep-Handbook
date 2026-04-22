# Food Delivery System - LLD Interview Solution 🍕
---

## 1) Requirements (~5 min)

**Prompt**: "Design a food delivery system like Zomato or Swiggy."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | Browse restaurants, add items to cart, place order, assign delivery, track status |
| **Primary capabilities** | Multiple restaurants per order? | No — one restaurant per order (simplifies logistics) |
| **Rules** | How is delivery assigned? | Nearest available delivery agent |
| **Rules** | How is pricing done? | Item prices + delivery fee (Strategy pattern for fee calculation) |
| **Rules** | Order lifecycle? | PLACED → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED / CANCELLED |
| **Error handling** | No delivery agent available? | Order stays CONFIRMED, retries assignment |
| **Error handling** | Restaurant out of stock? | Reject item, don't allow order |
| **Scope** | Concurrent access? | Yes — multiple orders, agents, restaurants simultaneously |

### Requirements

```
1. Restaurants register with a menu (items + prices + availability)
2. Customers browse restaurants and menus
3. Customers place orders from a single restaurant (cart → order)
4. System assigns nearest available delivery agent
5. Order lifecycle: PLACED → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
6. Customers can cancel orders (before PREPARING)
7. Delivery fee calculated via pluggable strategy
8. Prevent double-assignment of delivery agents under concurrent access

Out of Scope:
- Payment gateway integration
- Real GPS/location tracking
- Customer reviews and ratings
- Restaurant analytics
- Promotions and coupons
- Multi-restaurant orders
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Restaurant        (owns menu items, has location)
- MenuItem          (food item — name, price, available flag)
- Customer          (places orders, has location)
- DeliveryAgent     (delivers orders — owns availability state)
- Order             (links customer + restaurant + items + agent, tracks status)
- OrderItem         (quantity of a specific MenuItem)
- DeliveryFeeStrategy (interface — calculates delivery fee)
- OrderService      (orchestrator — place order, assign agent, update status)

Relationships:
- Restaurant → MenuItem (1:N, owns menu)
- Order → Customer, Restaurant, DeliveryAgent (references)
- Order → OrderItem (1:N, contains items)
- OrderItem → MenuItem (reference)
- OrderService → Restaurant, Customer, DeliveryAgent registries
- OrderService → DeliveryFeeStrategy (uses for fee calculation)
```

**Key decisions:**
- One restaurant per order — simplifies delivery logistics
- DeliveryAgent availability is the shared resource needing concurrency control
- Agent assignment uses agent-level locking (tryLock) — skip to next if busy
- DeliveryFeeStrategy is injected (Strategy pattern) — flat fee, distance-based, etc.
- Order status transitions are validated (can't skip states or go backwards)

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Restaurants with menus" | id, name, location, menuItems | Restaurant |
| "Menu items with availability" | id, name, price, available | MenuItem |
| "Customer places order" | id, name, location | Customer |
| "Assign nearest available agent" | id, name, location, available | DeliveryAgent |
| "Order lifecycle" | id, customer, restaurant, items, agent, status, fees | Order |
| "Delivery fee strategy" | calculateFee(distance) | DeliveryFeeStrategy |
| "Prevent double-assignment" | per-agent ReentrantLock | OrderService |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Place an order | placeOrder(customerId, restaurantId, itemQuantities) → Order | OrderService |
| Assign delivery agent | assignAgent(orderId) → boolean | OrderService |
| Update order status | updateStatus(orderId, newStatus) | OrderService |
| Cancel order | cancelOrder(orderId) | OrderService |
| Register restaurant/customer/agent | addRestaurant, addCustomer, addAgent | OrderService |

### Class Outlines

```
class MenuItem:
  - id, name: String
  - price: double
  - available: volatile boolean

class Restaurant:
  - id, name: String
  - location: double (simplified as distance from origin)
  - menu: Map<String, MenuItem>

  + getMenuItem(itemId) → MenuItem
  + getAvailableItems() → List<MenuItem>

class Customer:
  - id, name: String
  - location: double

class DeliveryAgent:                        // Shared resource — needs locking
  - id, name: String
  - location: double
  - available: volatile boolean

  + markBusy(), markAvailable()

class OrderItem:
  - menuItem: MenuItem
  - quantity: int
  - subtotal: double

class Order:
  - id: String (auto-generated)
  - customer: Customer
  - restaurant: Restaurant
  - items: List<OrderItem>
  - agent: volatile DeliveryAgent
  - status: volatile OrderStatus
  - itemTotal, deliveryFee, totalAmount: double

  + updateStatus(newStatus)                 // validates transitions

enum OrderStatus:
  PLACED → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
  Also: CANCELLED (from PLACED or CONFIRMED only)

interface DeliveryFeeStrategy:
  + calculateFee(restaurantLocation, customerLocation) → double

class FlatFeeStrategy implements DeliveryFeeStrategy    // fixed fee
class DistanceBasedFeeStrategy implements DeliveryFeeStrategy  // per-km rate

class OrderService:                         // Orchestrator
  - restaurants, customers, agents: Maps
  - agentLocks: ConcurrentHashMap<String, ReentrantLock>
  - orders: ConcurrentHashMap<String, Order>
  - feeStrategy: DeliveryFeeStrategy

  + placeOrder(customerId, restaurantId, Map<itemId, qty>) → Order
  + assignAgent(orderId) → boolean
  + updateStatus(orderId, newStatus)
  + cancelOrder(orderId)
```

### Key Principle

- **OrderService** owns the workflow: validate → create order → assign agent → track status
- **Restaurant** owns its menu (which items exist, availability)
- **Order** owns its status transitions (validates legal transitions)
- **DeliveryAgent** owns its availability flag (busy/available)
- **DeliveryFeeStrategy** owns fee calculation (swappable)

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- DeliveryAgent.available — multiple orders competing for the same agent
- Order.status — concurrent status updates (assign agent + customer cancel)

**What can go wrong?**
- Double-assignment: Two orders assigned the same delivery agent simultaneously
- Lost update: Agent marked busy by one thread, but another thread already read it as available

**What's the locking strategy?**
- Agent-level locking. Each delivery agent has its own ReentrantLock.

### Why Agent-Level Locking?

| Approach | Throughput | Decision |
|---|---|---|
| **Global lock** | Very low (serializes all assignments) | ❌ Too coarse |
| **Agent-level lock** | High (parallel across agents) | ✅ Chosen |

### Concurrency Strategy

```
Shared resource:
- DeliveryAgent.available — multiple orders trying to grab same agent

Race condition prevented:
- Double-assignment: tryLock + isAvailable + markBusy is atomic under lock

Locking approach:
- Each agent has its own ReentrantLock(true) — fair lock
- assignAgent uses tryLock() (non-blocking) — skip to next agent if locked
- Agent sorted by distance to restaurant — nearest first
- Order.updateStatus is synchronized (atomic status transitions)

Thread-safety:
- DeliveryAgent: volatile available + external lock
- Order: volatile status/agent + synchronized updateStatus
- MenuItem: volatile available
- Restaurant, Customer: immutable after creation
- Repositories: ConcurrentHashMap
```

**Why tryLock() for agent assignment (non-blocking)?**
An order doesn't care *which* agent it gets — if the nearest is locked, skip to the next nearest. No need to wait.

**No deadlock risk:**
Each assignment locks only one agent at a time — no multi-resource locking needed.

---

## 5) Implementation (~10 min)

### Core Method: placeOrder

```java
public Order placeOrder(String customerId, String restaurantId,
                        Map<String, Integer> itemQuantities) {
    Customer customer = customers.get(customerId);
    if (customer == null) throw new IllegalArgumentException("Customer not found");

    Restaurant restaurant = restaurants.get(restaurantId);
    if (restaurant == null) throw new IllegalArgumentException("Restaurant not found");

    // Build order items — validate availability
    List<OrderItem> orderItems = new ArrayList<>();
    double itemTotal = 0;
    for (var entry : itemQuantities.entrySet()) {
        MenuItem item = restaurant.getMenuItem(entry.getKey());
        if (item == null) throw new IllegalArgumentException("Item not found: " + entry.getKey());
        if (!item.isAvailable()) throw new IllegalStateException("Item unavailable: " + item.getName());
        OrderItem oi = new OrderItem(item, entry.getValue());
        orderItems.add(oi);
        itemTotal += oi.getSubtotal();
    }

    double deliveryFee = feeStrategy.calculateFee(restaurant.getLocation(), customer.getLocation());
    Order order = new Order(customer, restaurant, orderItems, itemTotal, deliveryFee);
    orders.put(order.getId(), order);
    return order;
}
```

### Core Method: assignAgent — Nearest Available with Locking

```java
public boolean assignAgent(String orderId) {
    Order order = orders.get(orderId);
    if (order == null) throw new IllegalArgumentException("Order not found");
    if (order.getAgent() != null) return true; // already assigned

    // Sort agents by distance to restaurant
    List<DeliveryAgent> sorted = new ArrayList<>(agents.values());
    sorted.sort(Comparator.comparingDouble(
            a -> Math.abs(a.getLocation() - order.getRestaurant().getLocation())));

    for (DeliveryAgent agent : sorted) {
        ReentrantLock lock = agentLocks.computeIfAbsent(agent.getId(),
                k -> new ReentrantLock(true));

        if (lock.tryLock()) {                    // Non-blocking — skip if busy
            try {
                if (agent.isAvailable()) {
                    agent.markBusy();
                    order.setAgent(agent);
                    order.updateStatus(OrderStatus.CONFIRMED);
                    return true;
                }
            } finally {
                lock.unlock();
            }
        }
    }
    return false; // no agent available right now
}
```

**What this demonstrates:**
- Agents sorted by distance (nearest first)
- tryLock() — non-blocking, skips to next agent if this one is contended
- Atomic isAvailable + markBusy under lock — no double-assignment
- Lock released in finally — even if markBusy() throws

### Core Method: updateStatus — Validated Transitions

```java
// On Order class
public synchronized void updateStatus(OrderStatus newStatus) {
    if (!isValidTransition(status, newStatus))
        throw new IllegalStateException("Invalid transition: " + status + " → " + newStatus);
    this.status = newStatus;
}

private boolean isValidTransition(OrderStatus from, OrderStatus to) {
    return switch (from) {
        case PLACED     -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
        case CONFIRMED  -> to == OrderStatus.PREPARING || to == OrderStatus.CANCELLED;
        case PREPARING  -> to == OrderStatus.OUT_FOR_DELIVERY;
        case OUT_FOR_DELIVERY -> to == OrderStatus.DELIVERED;
        default -> false;
    };
}
```

### Core Method: cancelOrder — Release Agent

```java
public void cancelOrder(String orderId) {
    Order order = orders.get(orderId);
    if (order == null) throw new IllegalArgumentException("Order not found");

    order.updateStatus(OrderStatus.CANCELLED); // validates transition

    // Release agent if assigned
    DeliveryAgent agent = order.getAgent();
    if (agent != null) {
        ReentrantLock lock = agentLocks.get(agent.getId());
        lock.lock();
        try {
            agent.markAvailable();
        } finally {
            lock.unlock();
        }
    }
}
```

### Verification: Walk Through a Scenario

```
Scenario: Two orders compete for the same nearest delivery agent

Order 1: placeOrder("C1", "R1", {burger: 2})
  → Order ORD-1001 created, status=PLACED, total=$25.98 + $5 delivery

Order 2: placeOrder("C2", "R1", {pizza: 1})
  → Order ORD-1002 created, status=PLACED, total=$15.99 + $5 delivery

Thread A: assignAgent("ORD-1001")
  → Sorted agents: [Agent1(dist=2), Agent2(dist=5)]
  → tryLock(Agent1) succeeds
  → Agent1.isAvailable() = true → markBusy()
  → ORD-1001.agent = Agent1, status = CONFIRMED
  → Releases lock

Thread B: assignAgent("ORD-1002")
  → Sorted agents: [Agent1(dist=2), Agent2(dist=5)]
  → tryLock(Agent1) succeeds (Thread A released)
  → Agent1.isAvailable() = false (already busy)
  → Moves to Agent2
  → tryLock(Agent2) succeeds → Agent2.isAvailable() = true → markBusy()
  → ORD-1002.agent = Agent2, status = CONFIRMED

✓ No double-assignment. Each agent assigned to exactly one order.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Place order with 2 items → verify itemTotal, deliveryFee, totalAmount
- Assign nearest agent → verify correct agent selected
- Full lifecycle: PLACED → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
- Cancel order before PREPARING → succeeds, agent released
- Cancel order during PREPARING → IllegalStateException

**Concurrency tests:**
- **Agent contention**: 10 orders, 3 agents → exactly 3 assigned, 7 fail
- **Parallel assignment**: 10 orders, 10 agents → all 10 assigned to different agents
- **Cancel + assign race**: cancel order while agent being assigned → no inconsistency

**Edge cases:**
- Order from non-existent restaurant → IllegalArgumentException
- Order with unavailable menu item → IllegalStateException
- Assign agent to already-assigned order → returns true (idempotent)
- No agents available → returns false
- Invalid status transition (DELIVERED → PLACED) → IllegalStateException

---

## 7) Extensibility (~5 min)

**"How would you add different delivery fee strategies (surge pricing, free delivery)?"**
> "DeliveryFeeStrategy is already an interface. Implement SurgePricing or FreeDeliveryAboveThreshold and inject into OrderService — no changes to existing code."

```java
class SurgePricingFee implements DeliveryFeeStrategy {
    public double calculateFee(double restaurantLoc, double customerLoc) {
        double base = Math.abs(restaurantLoc - customerLoc) * 5;
        return isPeakHour() ? base * 1.5 : base;
    }
}
```

**"How would you add restaurant ratings and sorting?"**
> "Add a rating field to Restaurant. Add a RestaurantSearchService that sorts by rating, distance, or a combination. The ordering and delivery logic doesn't change."

**"How would you add real-time order tracking?"**
> "Observer pattern. When order status changes, notify registered listeners (customer app, restaurant dashboard, agent app). Add an OrderObserver interface and fire events from updateStatus()."

**"How would you add promotions and coupons?"**
> "Add a DiscountStrategy interface applied before totalAmount calculation. Coupon validation happens in placeOrder. The delivery and agent assignment logic stays the same."

**"How would you scale to millions of orders?"**
> "Partition by city/zone — each zone has its own agent pool and restaurant registry. Replace in-memory maps with a database. Agent assignment can use a geo-spatial index (R-tree) instead of sorting all agents. The OrderService interface stays the same."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **placeOrder** | O(I) items validation | O(I) |
| **assignAgent** | O(A log A) sort + O(A) scan | O(A) |
| **updateStatus** | O(1) | O(1) |
| **cancelOrder** | O(1) | O(1) |

*I = items in order, A = total delivery agents. With geo-index: assignAgent becomes O(log A).*

---

**Implementation**: See [FoodDeliverySystemComplete.java](./FoodDeliverySystemComplete.java)
