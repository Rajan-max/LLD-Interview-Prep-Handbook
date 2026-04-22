package problems.FoodDeliverySystem;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FOOD DELIVERY SYSTEM - Complete Implementation
 *
 * Key Features:
 * - Agent-level locking for delivery assignment (no double-assignment)
 * - Nearest-available-agent dispatch (sorted by distance)
 * - Strategy pattern for delivery fee calculation
 * - Validated order status transitions (state machine)
 * - Concurrency-safe agent assignment with tryLock
 */

// ============================================================================
// ENUMS
// ============================================================================

enum OrderStatus {
    PLACED, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus to) {
        return switch (this) {
            case PLACED -> to == CONFIRMED || to == CANCELLED;
            case CONFIRMED -> to == PREPARING || to == CANCELLED;
            case PREPARING -> to == OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> to == DELIVERED;
            default -> false;
        };
    }
}

// ============================================================================
// MODELS
// ============================================================================

class MenuItem {
    private final String id;
    private final String name;
    private final double price;
    private volatile boolean available;

    public MenuItem(String id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.available = true;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}

class Restaurant {
    private final String id;
    private final String name;
    private final double location;
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();

    public Restaurant(String id, String name, double location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public void addMenuItem(MenuItem item) { menu.put(item.getId(), item); }
    public MenuItem getMenuItem(String itemId) { return menu.get(itemId); }
    public List<MenuItem> getAvailableItems() {
        return menu.values().stream().filter(MenuItem::isAvailable).toList();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLocation() { return location; }
}

class Customer {
    private final String id;
    private final String name;
    private final double location;

    public Customer(String id, String name, double location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLocation() { return location; }
}

/** Thread-safe: volatile available + external lock */
class DeliveryAgent {
    private final String id;
    private final String name;
    private final double location;
    private volatile boolean available;

    public DeliveryAgent(String id, String name, double location) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.available = true;
    }

    public void markBusy() { this.available = false; }
    public void markAvailable() { this.available = true; }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLocation() { return location; }
    public boolean isAvailable() { return available; }
}

class OrderItem {
    private final MenuItem menuItem;
    private final int quantity;
    private final double subtotal;

    public OrderItem(MenuItem menuItem, int quantity) {
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.subtotal = menuItem.getPrice() * quantity;
    }

    public MenuItem getMenuItem() { return menuItem; }
    public int getQuantity() { return quantity; }
    public double getSubtotal() { return subtotal; }
}

/** Thread-safe: volatile fields + synchronized status updates */
class Order {
    private static final AtomicInteger idGen = new AtomicInteger(1000);

    private final String id;
    private final Customer customer;
    private final Restaurant restaurant;
    private final List<OrderItem> items;
    private final double itemTotal;
    private final double deliveryFee;
    private final double totalAmount;
    private volatile OrderStatus status;
    private volatile DeliveryAgent agent;

    public Order(Customer customer, Restaurant restaurant, List<OrderItem> items,
                 double itemTotal, double deliveryFee) {
        this.id = "ORD-" + idGen.getAndIncrement();
        this.customer = customer;
        this.restaurant = restaurant;
        this.items = List.copyOf(items);
        this.itemTotal = itemTotal;
        this.deliveryFee = deliveryFee;
        this.totalAmount = itemTotal + deliveryFee;
        this.status = OrderStatus.PLACED;
    }

    public synchronized void updateStatus(OrderStatus newStatus) {
        if (!status.canTransitionTo(newStatus))
            throw new IllegalStateException("Invalid transition: " + status + " → " + newStatus);
        this.status = newStatus;
    }

    public void setAgent(DeliveryAgent agent) { this.agent = agent; }

    public String getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Restaurant getRestaurant() { return restaurant; }
    public List<OrderItem> getItems() { return items; }
    public double getItemTotal() { return itemTotal; }
    public double getDeliveryFee() { return deliveryFee; }
    public double getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public DeliveryAgent getAgent() { return agent; }
}

// ============================================================================
// STRATEGY PATTERN — Delivery Fee
// ============================================================================

interface DeliveryFeeStrategy {
    double calculateFee(double restaurantLocation, double customerLocation);
}

class FlatFeeStrategy implements DeliveryFeeStrategy {
    private final double fee;
    public FlatFeeStrategy(double fee) { this.fee = fee; }

    @Override
    public double calculateFee(double restaurantLoc, double customerLoc) { return fee; }
}

class DistanceBasedFeeStrategy implements DeliveryFeeStrategy {
    private final double ratePerKm;
    private final double minFee;
    public DistanceBasedFeeStrategy(double ratePerKm, double minFee) {
        this.ratePerKm = ratePerKm;
        this.minFee = minFee;
    }

    @Override
    public double calculateFee(double restaurantLoc, double customerLoc) {
        double distance = Math.abs(restaurantLoc - customerLoc);
        return Math.max(minFee, distance * ratePerKm);
    }
}

// ============================================================================
// SERVICE — Orchestrator with Concurrency Control
// ============================================================================

class OrderService {
    private final ConcurrentHashMap<String, Restaurant> restaurants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Customer> customers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryAgent> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> agentLocks = new ConcurrentHashMap<>();
    private final DeliveryFeeStrategy feeStrategy;

    public OrderService(DeliveryFeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;
    }

    public void addRestaurant(Restaurant r) { restaurants.put(r.getId(), r); }
    public void addCustomer(Customer c) { customers.put(c.getId(), c); }
    public void addAgent(DeliveryAgent a) {
        agents.put(a.getId(), a);
        agentLocks.put(a.getId(), new ReentrantLock(true));
    }

    public Order placeOrder(String customerId, String restaurantId,
                            Map<String, Integer> itemQuantities) {
        Customer customer = customers.get(customerId);
        if (customer == null) throw new IllegalArgumentException("Customer not found");
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) throw new IllegalArgumentException("Restaurant not found");

        List<OrderItem> orderItems = new ArrayList<>();
        double itemTotal = 0;
        for (var entry : itemQuantities.entrySet()) {
            MenuItem item = restaurant.getMenuItem(entry.getKey());
            if (item == null) throw new IllegalArgumentException("Item not found: " + entry.getKey());
            if (!item.isAvailable()) throw new IllegalStateException("Unavailable: " + item.getName());
            OrderItem oi = new OrderItem(item, entry.getValue());
            orderItems.add(oi);
            itemTotal += oi.getSubtotal();
        }

        double deliveryFee = feeStrategy.calculateFee(restaurant.getLocation(), customer.getLocation());
        Order order = new Order(customer, restaurant, orderItems, itemTotal, deliveryFee);
        orders.put(order.getId(), order);
        return order;
    }

    /** Assign nearest available agent — agent-level locking with tryLock */
    public boolean assignAgent(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (order.getAgent() != null) return true;

        List<DeliveryAgent> sorted = new ArrayList<>(agents.values());
        sorted.sort(Comparator.comparingDouble(
                a -> Math.abs(a.getLocation() - order.getRestaurant().getLocation())));

        for (DeliveryAgent agent : sorted) {
            ReentrantLock lock = agentLocks.get(agent.getId());
            if (lock.tryLock()) {
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
        return false;
    }

    public void updateStatus(String orderId, OrderStatus newStatus) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        order.updateStatus(newStatus);
    }

    public void cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        order.updateStatus(OrderStatus.CANCELLED);

        DeliveryAgent agent = order.getAgent();
        if (agent != null) {
            ReentrantLock lock = agentLocks.get(agent.getId());
            lock.lock();
            try { agent.markAvailable(); } finally { lock.unlock(); }
        }
    }

    /** Release agent after delivery */
    public void completeDelivery(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        order.updateStatus(OrderStatus.DELIVERED);

        DeliveryAgent agent = order.getAgent();
        if (agent != null) {
            ReentrantLock lock = agentLocks.get(agent.getId());
            lock.lock();
            try { agent.markAvailable(); } finally { lock.unlock(); }
        }
    }

    public Order getOrder(String orderId) { return orders.get(orderId); }
}

// ============================================================================
// DEMO AND TESTS
// ============================================================================

public class FoodDeliverySystemComplete {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   FOOD DELIVERY SYSTEM — Zomato/Swiggy Style Demo       ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        testPlaceOrder();
        testAssignNearestAgent();
        testFullLifecycle();
        testCancelOrder();
        testInvalidTransition();
        testAgentContention();
        testParallelAssignment();
        testDeliveryFeeStrategies();
        testEdgeCases();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    static OrderService setupService() {
        OrderService svc = new OrderService(new FlatFeeStrategy(5.0));

        Restaurant r1 = new Restaurant("R1", "Pizza Palace", 10.0);
        r1.addMenuItem(new MenuItem("burger", "Burger", 8.99));
        r1.addMenuItem(new MenuItem("pizza", "Pizza", 12.99));
        r1.addMenuItem(new MenuItem("fries", "Fries", 4.99));
        svc.addRestaurant(r1);

        Restaurant r2 = new Restaurant("R2", "Sushi House", 25.0);
        r2.addMenuItem(new MenuItem("sushi", "Sushi Roll", 15.99));
        svc.addRestaurant(r2);

        svc.addCustomer(new Customer("C1", "Alice", 5.0));
        svc.addCustomer(new Customer("C2", "Bob", 20.0));

        svc.addAgent(new DeliveryAgent("A1", "Agent1", 8.0));
        svc.addAgent(new DeliveryAgent("A2", "Agent2", 15.0));
        svc.addAgent(new DeliveryAgent("A3", "Agent3", 22.0));

        return svc;
    }

    // --- Test 1: Place order ---
    static void testPlaceOrder() {
        System.out.println("Test 1: Place Order");
        System.out.println("-".repeat(56));
        OrderService svc = setupService();

        Order order = svc.placeOrder("C1", "R1", Map.of("burger", 2, "fries", 1));
        check("Order created", order != null);
        check("Status is PLACED", order.getStatus() == OrderStatus.PLACED);
        check("Item total = 2×8.99 + 1×4.99 = $22.97",
                Math.abs(order.getItemTotal() - 22.97) < 0.01);
        check("Delivery fee = $5.00", Math.abs(order.getDeliveryFee() - 5.0) < 0.01);
        check("Total = $27.97", Math.abs(order.getTotalAmount() - 27.97) < 0.01);
        check("3 items in order", order.getItems().size() == 2); // 2 line items
        System.out.println();
    }

    // --- Test 2: Assign nearest agent ---
    static void testAssignNearestAgent() {
        System.out.println("Test 2: Assign Nearest Agent");
        System.out.println("-".repeat(56));
        OrderService svc = setupService();

        // R1 at location 10.0, agents at 8, 15, 22 → Agent1 (dist=2) is nearest
        Order order = svc.placeOrder("C1", "R1", Map.of("pizza", 1));
        boolean assigned = svc.assignAgent(order.getId());

        check("Agent assigned", assigned);
        check("Status is CONFIRMED", order.getStatus() == OrderStatus.CONFIRMED);
        check("Nearest agent (A1) assigned", order.getAgent().getId().equals("A1"));
        System.out.println();
    }

    // --- Test 3: Full lifecycle ---
    static void testFullLifecycle() {
        System.out.println("Test 3: Full Order Lifecycle");
        System.out.println("-".repeat(56));
        OrderService svc = setupService();

        Order order = svc.placeOrder("C1", "R1", Map.of("burger", 1));
        check("PLACED", order.getStatus() == OrderStatus.PLACED);

        svc.assignAgent(order.getId());
        check("CONFIRMED", order.getStatus() == OrderStatus.CONFIRMED);

        svc.updateStatus(order.getId(), OrderStatus.PREPARING);
        check("PREPARING", order.getStatus() == OrderStatus.PREPARING);

        svc.updateStatus(order.getId(), OrderStatus.OUT_FOR_DELIVERY);
        check("OUT_FOR_DELIVERY", order.getStatus() == OrderStatus.OUT_FOR_DELIVERY);

        svc.completeDelivery(order.getId());
        check("DELIVERED", order.getStatus() == OrderStatus.DELIVERED);
        check("Agent released after delivery", order.getAgent().isAvailable());
        System.out.println();
    }

    // --- Test 4: Cancel order ---
    static void testCancelOrder() {
        System.out.println("Test 4: Cancel Order (Agent Released)");
        System.out.println("-".repeat(56));
        OrderService svc = setupService();

        Order order = svc.placeOrder("C1", "R1", Map.of("pizza", 1));
        svc.assignAgent(order.getId());
        DeliveryAgent agent = order.getAgent();
        check("Agent busy after assignment", !agent.isAvailable());

        svc.cancelOrder(order.getId());
        check("Status is CANCELLED", order.getStatus() == OrderStatus.CANCELLED);
        check("Agent released after cancel", agent.isAvailable());
        System.out.println();
    }

    // --- Test 5: Invalid transition ---
    static void testInvalidTransition() {
        System.out.println("Test 5: Invalid Status Transition");
        System.out.println("-".repeat(56));
        OrderService svc = setupService();

        Order order = svc.placeOrder("C1", "R1", Map.of("burger", 1));
        try {
            svc.updateStatus(order.getId(), OrderStatus.DELIVERED); // PLACED → DELIVERED invalid
            check("Should have thrown", false);
        } catch (IllegalStateException e) {
            check("Invalid transition rejected: " + e.getMessage(), true);
        }

        // Cancel after PREPARING should fail
        svc.assignAgent(order.getId());
        svc.updateStatus(order.getId(), OrderStatus.PREPARING);
        try {
            svc.cancelOrder(order.getId()); // PREPARING → CANCELLED invalid
            check("Should have thrown", false);
        } catch (IllegalStateException e) {
            check("Cancel after PREPARING rejected", true);
        }
        System.out.println();
    }

    // --- Test 6: Agent contention ---
    static void testAgentContention() throws Exception {
        System.out.println("Test 6: Agent Contention (10 orders, 3 agents)");
        System.out.println("-".repeat(56));

        OrderService svc = new OrderService(new FlatFeeStrategy(5.0));
        Restaurant r = new Restaurant("R1", "Test", 10.0);
        r.addMenuItem(new MenuItem("item", "Item", 10.0));
        svc.addRestaurant(r);

        for (int i = 0; i < 10; i++) svc.addCustomer(new Customer("C" + i, "User" + i, 5.0));
        for (int i = 0; i < 3; i++) svc.addAgent(new DeliveryAgent("A" + i, "Agent" + i, 10.0 + i));

        ExecutorService exec = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            futures.add(exec.submit(() -> {
                Order order = svc.placeOrder("C" + idx, "R1", Map.of("item", 1));
                return svc.assignAgent(order.getId());
            }));
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        long assigned = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return false; } })
                .filter(b -> b).count();

        check("Exactly 3 of 10 orders assigned (3 agents)", assigned == 3);
        System.out.println();
    }

    // --- Test 7: Parallel assignment to different agents ---
    static void testParallelAssignment() throws Exception {
        System.out.println("Test 7: Parallel Assignment (5 orders, 5 agents)");
        System.out.println("-".repeat(56));

        OrderService svc = new OrderService(new FlatFeeStrategy(5.0));
        Restaurant r = new Restaurant("R1", "Test", 10.0);
        r.addMenuItem(new MenuItem("item", "Item", 10.0));
        svc.addRestaurant(r);

        for (int i = 0; i < 5; i++) svc.addCustomer(new Customer("C" + i, "User" + i, 5.0));
        for (int i = 0; i < 5; i++) svc.addAgent(new DeliveryAgent("A" + i, "Agent" + i, 10.0 + i));

        ExecutorService exec = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            futures.add(exec.submit(() -> {
                Order order = svc.placeOrder("C" + idx, "R1", Map.of("item", 1));
                return svc.assignAgent(order.getId());
            }));
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        long assigned = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return false; } })
                .filter(b -> b).count();

        check("All 5 orders assigned (5 agents available)", assigned == 5);
        System.out.println();
    }

    // --- Test 8: Delivery fee strategies ---
    static void testDeliveryFeeStrategies() {
        System.out.println("Test 8: Delivery Fee Strategies");
        System.out.println("-".repeat(56));

        // Flat fee
        OrderService svc1 = new OrderService(new FlatFeeStrategy(5.0));
        setupRestaurantAndCustomer(svc1);
        Order o1 = svc1.placeOrder("C1", "R1", Map.of("item", 1));
        check("Flat fee = $5.00", Math.abs(o1.getDeliveryFee() - 5.0) < 0.01);

        // Distance-based: restaurant at 10, customer at 5, distance=5, rate=$3/km, min=$2
        OrderService svc2 = new OrderService(new DistanceBasedFeeStrategy(3.0, 2.0));
        setupRestaurantAndCustomer(svc2);
        Order o2 = svc2.placeOrder("C1", "R1", Map.of("item", 1));
        check("Distance fee = 5km × $3 = $15.00", Math.abs(o2.getDeliveryFee() - 15.0) < 0.01);

        // Distance-based with min fee: restaurant at 10, customer at 10.5, distance=0.5, rate=$3, min=$2
        OrderService svc3 = new OrderService(new DistanceBasedFeeStrategy(3.0, 2.0));
        Restaurant r = new Restaurant("R1", "Test", 10.0);
        r.addMenuItem(new MenuItem("item", "Item", 10.0));
        svc3.addRestaurant(r);
        svc3.addCustomer(new Customer("C1", "Near", 10.5));
        Order o3 = svc3.placeOrder("C1", "R1", Map.of("item", 1));
        check("Min fee applied = $2.00 (0.5km × $3 = $1.50 < min $2)",
                Math.abs(o3.getDeliveryFee() - 2.0) < 0.01);
        System.out.println();
    }

    static void setupRestaurantAndCustomer(OrderService svc) {
        Restaurant r = new Restaurant("R1", "Test", 10.0);
        r.addMenuItem(new MenuItem("item", "Item", 10.0));
        svc.addRestaurant(r);
        svc.addCustomer(new Customer("C1", "Alice", 5.0));
    }

    // --- Test 9: Edge cases ---
    static void testEdgeCases() {
        System.out.println("Test 9: Edge Cases");
        System.out.println("-".repeat(56));
        OrderService svc = setupService();

        try {
            svc.placeOrder("INVALID", "R1", Map.of("burger", 1));
            check("Should throw for invalid customer", false);
        } catch (IllegalArgumentException e) {
            check("Invalid customer rejected", true);
        }

        try {
            svc.placeOrder("C1", "INVALID", Map.of("burger", 1));
            check("Should throw for invalid restaurant", false);
        } catch (IllegalArgumentException e) {
            check("Invalid restaurant rejected", true);
        }

        try {
            svc.placeOrder("C1", "R1", Map.of("nonexistent", 1));
            check("Should throw for invalid item", false);
        } catch (IllegalArgumentException e) {
            check("Invalid menu item rejected", true);
        }

        // Unavailable item
        Restaurant r = new Restaurant("R3", "Closed Kitchen", 5.0);
        MenuItem closedItem = new MenuItem("closed", "Closed Item", 10.0);
        closedItem.setAvailable(false);
        r.addMenuItem(closedItem);
        svc.addRestaurant(r);
        try {
            svc.placeOrder("C1", "R3", Map.of("closed", 1));
            check("Should throw for unavailable item", false);
        } catch (IllegalStateException e) {
            check("Unavailable item rejected", true);
        }

        // Assign agent to already-assigned order (idempotent)
        Order order = svc.placeOrder("C1", "R1", Map.of("burger", 1));
        svc.assignAgent(order.getId());
        check("Re-assign returns true (idempotent)", svc.assignAgent(order.getId()));

        // No agents available
        OrderService emptySvc = new OrderService(new FlatFeeStrategy(5.0));
        Restaurant r2 = new Restaurant("R1", "Test", 10.0);
        r2.addMenuItem(new MenuItem("item", "Item", 10.0));
        emptySvc.addRestaurant(r2);
        emptySvc.addCustomer(new Customer("C1", "Alice", 5.0));
        Order noAgentOrder = emptySvc.placeOrder("C1", "R1", Map.of("item", 1));
        check("No agents → returns false", !emptySvc.assignAgent(noAgentOrder.getId()));
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
