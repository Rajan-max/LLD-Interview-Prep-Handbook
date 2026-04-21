package oops;

import java.util.*;

/**
 * OOP CONCEPTS FOR LLD INTERVIEWS — Complete Runnable Examples
 *
 * Covers:
 * 1. Encapsulation      — Private state, controlled access
 * 2. Abstraction         — Interfaces hiding complexity
 * 3. Inheritance         — "is-a" with shared state
 * 4. Polymorphism        — Same method, different behavior
 * 5. Composition         — "has-a" relationships (favored over inheritance)
 * 6. Interfaces vs Abstract Classes
 * 7. Enums with behavior — Type-safe constants with state machines
 */
public class OOPConcepts {

    // ========================================================================
    // 1. ENCAPSULATION — Private state + controlled access
    // ========================================================================

    /**
     * BAD: Exposed fields — anyone can set invalid state
     */
    static class BankAccountBad {
        public double balance;  // Anyone can set this to -9999!
        public String owner;
    }

    /**
     * GOOD: Private fields + validation in methods
     * Only deposit() and withdraw() can change balance, with rules enforced.
     */
    static class BankAccount {
        private final String owner;
        private double balance;

        public BankAccount(String owner, double initialBalance) {
            if (initialBalance < 0) throw new IllegalArgumentException("Negative balance");
            this.owner = owner;
            this.balance = initialBalance;
        }

        public void deposit(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            this.balance += amount;
        }

        public void withdraw(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            if (amount > balance) throw new IllegalStateException("Insufficient funds");
            this.balance -= amount;
        }

        public double getBalance() { return balance; }
        public String getOwner() { return owner; }

        @Override
        public String toString() {
            return owner + ": $" + String.format("%.2f", balance);
        }
    }

    // ========================================================================
    // 2. ABSTRACTION — Hide complexity behind interfaces
    // ========================================================================

    /**
     * Interface defines WHAT — the contract.
     * Callers only know "send(recipient, message)" — not HOW it's sent.
     */
    interface NotificationSender {
        void send(String recipient, String message);
    }

    static class EmailSender implements NotificationSender {
        @Override
        public void send(String recipient, String message) {
            // SMTP details hidden inside
            System.out.println("    📧 Email to " + recipient + ": " + message);
        }
    }

    static class SMSSender implements NotificationSender {
        @Override
        public void send(String recipient, String message) {
            // Twilio API details hidden inside
            System.out.println("    📱 SMS to " + recipient + ": " + message);
        }
    }

    static class PushSender implements NotificationSender {
        @Override
        public void send(String recipient, String message) {
            System.out.println("    🔔 Push to " + recipient + ": " + message);
        }
    }

    /**
     * NotificationService doesn't know or care about Email/SMS/Push.
     * It only knows the NotificationSender interface.
     * Adding a new channel (e.g., Slack) requires ZERO changes here.
     */
    static class NotificationService {
        private final List<NotificationSender> senders;

        public NotificationService(List<NotificationSender> senders) {
            this.senders = senders;
        }

        public void notifyUser(String recipient, String message) {
            for (NotificationSender sender : senders) {
                sender.send(recipient, message);
            }
        }
    }

    // ========================================================================
    // 3. INHERITANCE — "is-a" with shared state
    // ========================================================================

    enum VehicleType { BIKE, CAR, TRUCK }

    /**
     * Abstract base class — shared state (licensePlate, type) for all vehicles.
     * Subtypes only set their specific type via constructor.
     */
    static abstract class Vehicle {
        private final String licensePlate;
        private final VehicleType type;

        public Vehicle(String licensePlate, VehicleType type) {
            this.licensePlate = licensePlate;
            this.type = type;
        }

        public String getLicensePlate() { return licensePlate; }
        public VehicleType getType() { return type; }

        @Override
        public String toString() { return type + "(" + licensePlate + ")"; }
    }

    static class Bike extends Vehicle {
        public Bike(String licensePlate) { super(licensePlate, VehicleType.BIKE); }
    }

    static class Car extends Vehicle {
        public Car(String licensePlate) { super(licensePlate, VehicleType.CAR); }
    }

    static class Truck extends Vehicle {
        public Truck(String licensePlate) { super(licensePlate, VehicleType.TRUCK); }
    }

    // ========================================================================
    // 4. POLYMORPHISM — Same method, different behavior
    // ========================================================================

    // --- 4a. Compile-Time Polymorphism (Method Overloading) ---

    static class FeeCalculator {
        public double calculate(int hours) {
            return hours * 10.0;
        }

        public double calculate(int hours, double multiplier) {
            return hours * 10.0 * multiplier;
        }
    }

    // --- 4b. Runtime Polymorphism (Method Overriding) ⭐ ---

    interface PricingStrategy {
        double calculateFee(VehicleType type, long hours);
    }

    static class HourlyPricing implements PricingStrategy {
        @Override
        public double calculateFee(VehicleType type, long hours) {
            return switch (type) {
                case BIKE  -> hours * 10;
                case CAR   -> hours * 20;
                case TRUCK -> hours * 30;
            };
        }

        @Override
        public String toString() { return "HourlyPricing"; }
    }

    static class FlatRatePricing implements PricingStrategy {
        @Override
        public double calculateFee(VehicleType type, long hours) {
            return switch (type) {
                case BIKE  -> 50;
                case CAR   -> 100;
                case TRUCK -> 200;
            };
        }

        @Override
        public String toString() { return "FlatRatePricing"; }
    }

    /**
     * ParkingFeeService doesn't know which strategy it's using.
     * The correct calculateFee() is called at RUNTIME based on the actual object.
     */
    static class ParkingFeeService {
        private final PricingStrategy strategy;

        public ParkingFeeService(PricingStrategy strategy) {
            this.strategy = strategy;
        }

        public double getFee(VehicleType type, long hours) {
            return strategy.calculateFee(type, hours);
        }

        public String getStrategyName() { return strategy.toString(); }
    }

    // ========================================================================
    // 5. COMPOSITION — "has-a" relationships (favored over inheritance)
    // ========================================================================

    static class ParkingSlot {
        private final String id;
        private final VehicleType type;
        private boolean occupied;
        private Vehicle vehicle;

        public ParkingSlot(String id, VehicleType type) {
            this.id = id;
            this.type = type;
        }

        public boolean canFit(Vehicle v) { return !occupied && type == v.getType(); }

        public void park(Vehicle v) {
            if (!canFit(v)) throw new IllegalStateException("Cannot park here");
            this.vehicle = v;
            this.occupied = true;
        }

        public void free() {
            this.vehicle = null;
            this.occupied = false;
        }

        public String getId() { return id; }
        public boolean isOccupied() { return occupied; }
        public Vehicle getVehicle() { return vehicle; }
        public VehicleType getType() { return type; }
    }

    /**
     * Floor HAS slots (composition, not inheritance).
     * Floor doesn't extend List<ParkingSlot> — it CONTAINS a list.
     */
    static class Floor {
        private final int number;
        private final List<ParkingSlot> slots;

        public Floor(int number, List<ParkingSlot> slots) {
            this.number = number;
            this.slots = slots;
        }

        public int getNumber() { return number; }
        public List<ParkingSlot> getSlots() { return Collections.unmodifiableList(slots); }

        public long getAvailableCount(VehicleType type) {
            return slots.stream().filter(s -> s.getType() == type && !s.isOccupied()).count();
        }
    }

    /**
     * ParkingLot HAS floors, HAS a pricing strategy.
     * Composition at every level: ParkingLot → Floor → ParkingSlot
     */
    static class ParkingLot {
        private final String name;
        private final List<Floor> floors;
        private final PricingStrategy pricing;

        public ParkingLot(String name, List<Floor> floors, PricingStrategy pricing) {
            this.name = name;
            this.floors = floors;
            this.pricing = pricing;
        }

        public ParkingSlot findSlot(Vehicle vehicle) {
            for (Floor floor : floors) {
                for (ParkingSlot slot : floor.getSlots()) {
                    if (slot.canFit(vehicle)) return slot;
                }
            }
            return null;
        }

        public String getName() { return name; }
        public List<Floor> getFloors() { return Collections.unmodifiableList(floors); }
        public PricingStrategy getPricing() { return pricing; }
    }

    // ========================================================================
    // 6. INTERFACES vs ABSTRACT CLASSES
    // ========================================================================

    // --- Interface: Pure contract, no state ---
    interface Searchable {
        List<String> search(String query);
    }

    // --- Abstract Class: Shared state + partial implementation ---
    static abstract class Notification {
        private final String id;
        private final String recipient;
        private final String message;
        private boolean sent;

        public Notification(String id, String recipient, String message) {
            this.id = id;
            this.recipient = recipient;
            this.message = message;
        }

        // Concrete method — shared by all subclasses
        public void markSent() { this.sent = true; }
        public boolean isSent() { return sent; }

        // Abstract method — each subclass implements differently
        public abstract void deliver();

        public String getRecipient() { return recipient; }
        public String getMessage() { return message; }
    }

    static class EmailNotification extends Notification {
        public EmailNotification(String id, String recipient, String message) {
            super(id, recipient, message);
        }

        @Override
        public void deliver() {
            System.out.println("    📧 Delivering email to " + getRecipient() + ": " + getMessage());
            markSent();
        }
    }

    static class SMSNotification extends Notification {
        public SMSNotification(String id, String recipient, String message) {
            super(id, recipient, message);
        }

        @Override
        public void deliver() {
            System.out.println("    📱 Delivering SMS to " + getRecipient() + ": " + getMessage());
            markSent();
        }
    }

    // ========================================================================
    // 7. ENUMS WITH BEHAVIOR — Type-safe constants + state machines
    // ========================================================================

    enum BookingStatus {
        PENDING {
            @Override public BookingStatus confirm() { return CONFIRMED; }
            @Override public BookingStatus cancel() { return CANCELLED; }
        },
        CONFIRMED {
            @Override public BookingStatus confirm() {
                throw new IllegalStateException("Already confirmed");
            }
            @Override public BookingStatus cancel() { return CANCELLED; }
        },
        CANCELLED {
            @Override public BookingStatus confirm() {
                throw new IllegalStateException("Cannot confirm cancelled booking");
            }
            @Override public BookingStatus cancel() {
                throw new IllegalStateException("Already cancelled");
            }
        };

        public abstract BookingStatus confirm();
        public abstract BookingStatus cancel();
    }

    // ========================================================================
    // DEMO — Run all examples
    // ========================================================================

    public static void main(String[] args) {

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     OOP CONCEPTS FOR LLD INTERVIEWS — DEMO      ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        // --- 1. Encapsulation ---
        System.out.println("━━━ 1. ENCAPSULATION ━━━");
        System.out.println("Private state + controlled access with validation\n");

        BankAccount account = new BankAccount("Alice", 1000);
        System.out.println("  Created: " + account);
        account.deposit(500);
        System.out.println("  After deposit(500): " + account);
        account.withdraw(200);
        System.out.println("  After withdraw(200): " + account);

        try {
            account.withdraw(5000);
        } catch (IllegalStateException e) {
            System.out.println("  withdraw(5000) → ❌ " + e.getMessage());
        }

        try {
            account.deposit(-100);
        } catch (IllegalArgumentException e) {
            System.out.println("  deposit(-100) → ❌ " + e.getMessage());
        }

        // --- 2. Abstraction ---
        System.out.println("\n━━━ 2. ABSTRACTION ━━━");
        System.out.println("NotificationService only knows the interface, not Email/SMS/Push\n");

        NotificationService service = new NotificationService(List.of(
                new EmailSender(), new SMSSender(), new PushSender()
        ));
        service.notifyUser("user@example.com", "Your booking is confirmed!");

        // --- 3. Inheritance ---
        System.out.println("\n━━━ 3. INHERITANCE ━━━");
        System.out.println("Vehicle → Bike, Car, Truck (shared state: licensePlate, type)\n");

        List<Vehicle> vehicles = List.of(
                new Bike("BK-001"), new Car("CR-042"), new Truck("TK-007")
        );
        for (Vehicle v : vehicles) {
            System.out.println("  " + v + " → type: " + v.getType());
        }

        // --- 4. Polymorphism ---
        System.out.println("\n━━━ 4. POLYMORPHISM ━━━");

        System.out.println("Compile-time (overloading):");
        FeeCalculator calc = new FeeCalculator();
        System.out.println("  calculate(3) = $" + calc.calculate(3));
        System.out.println("  calculate(3, 2.0) = $" + calc.calculate(3, 2.0));

        System.out.println("\nRuntime (overriding) — same call, different behavior:");
        ParkingFeeService hourly = new ParkingFeeService(new HourlyPricing());
        ParkingFeeService flat = new ParkingFeeService(new FlatRatePricing());

        System.out.println("  " + hourly.getStrategyName() + " → CAR, 3hrs = $" + hourly.getFee(VehicleType.CAR, 3));
        System.out.println("  " + flat.getStrategyName() + "  → CAR, 3hrs = $" + flat.getFee(VehicleType.CAR, 3));

        // --- 5. Composition ---
        System.out.println("\n━━━ 5. COMPOSITION ━━━");
        System.out.println("ParkingLot → Floor → ParkingSlot (has-a at every level)\n");

        List<ParkingSlot> f1Slots = List.of(
                new ParkingSlot("F1-B1", VehicleType.BIKE),
                new ParkingSlot("F1-C1", VehicleType.CAR),
                new ParkingSlot("F1-C2", VehicleType.CAR),
                new ParkingSlot("F1-T1", VehicleType.TRUCK)
        );
        Floor floor1 = new Floor(1, new ArrayList<>(f1Slots));
        ParkingLot lot = new ParkingLot("City Mall Parking", List.of(floor1), new HourlyPricing());

        System.out.println("  " + lot.getName() + " — Floor 1:");
        System.out.println("    Available BIKE slots: " + floor1.getAvailableCount(VehicleType.BIKE));
        System.out.println("    Available CAR slots:  " + floor1.getAvailableCount(VehicleType.CAR));

        Vehicle car = new Car("CR-100");
        ParkingSlot slot = lot.findSlot(car);
        if (slot != null) {
            slot.park(car);
            System.out.println("  Parked " + car + " in slot " + slot.getId());
            System.out.println("    Available CAR slots after parking: " + floor1.getAvailableCount(VehicleType.CAR));
        }

        // --- 6. Interfaces vs Abstract Classes ---
        System.out.println("\n━━━ 6. INTERFACES vs ABSTRACT CLASSES ━━━");
        System.out.println("Abstract class shares state (id, recipient, sent); subclasses implement deliver()\n");

        List<Notification> notifications = List.of(
                new EmailNotification("N1", "alice@example.com", "Welcome!"),
                new SMSNotification("N2", "+1234567890", "Your OTP is 4829")
        );
        for (Notification n : notifications) {
            n.deliver();
            System.out.println("    Sent: " + n.isSent());
        }

        // --- 7. Enums with Behavior ---
        System.out.println("\n━━━ 7. ENUMS WITH BEHAVIOR ━━━");
        System.out.println("BookingStatus state machine: PENDING → CONFIRMED → CANCELLED\n");

        BookingStatus status = BookingStatus.PENDING;
        System.out.println("  Status: " + status);

        status = status.confirm();
        System.out.println("  After confirm(): " + status);

        status = status.cancel();
        System.out.println("  After cancel(): " + status);

        try {
            status.confirm();
        } catch (IllegalStateException e) {
            System.out.println("  confirm() on CANCELLED → ❌ " + e.getMessage());
        }

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  All OOP concepts demonstrated successfully! ✅");
        System.out.println("══════════════════════════════════════════════════");
    }
}
