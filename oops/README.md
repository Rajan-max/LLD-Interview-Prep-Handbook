# OOP Concepts for LLD Interviews 🧬

> **Master Object-Oriented Programming to build clean, extensible low-level designs**

---

## 📚 Table of Contents

1. [Why OOP Matters for LLD](#-why-oop-matters-for-lld)
2. [The 4 Pillars of OOP](#-the-4-pillars-of-oop)
   - [Encapsulation](#1-encapsulation-)
   - [Abstraction](#2-abstraction-)
   - [Inheritance](#3-inheritance-)
   - [Polymorphism](#4-polymorphism-)
3. [Essential OOP Concepts](#-essential-oop-concepts)
   - [Composition vs Inheritance](#5-composition-vs-inheritance-)
   - [Interfaces vs Abstract Classes](#6-interfaces-vs-abstract-classes-)
   - [Enums](#7-enums-)
4. [OOP in LLD Problems — Quick Map](#-oop-in-lld-problems--quick-map)
5. [Common Interview Mistakes](#-common-interview-mistakes)
6. [Quick Reference Card](#-quick-reference-card)

---

## 🎯 Why OOP Matters for LLD

Every LLD interview answer is built on OOP fundamentals. If your OOP foundation is weak, your class design, pattern usage, and extensibility arguments will fall apart.

| OOP Concept | Where It Shows Up in LLD |
|---|---|
| **Encapsulation** | Hiding internal state of entities (Slot, Room, Seat) |
| **Abstraction** | Defining contracts (PricingStrategy, NotificationSender) |
| **Inheritance** | Modeling "is-a" hierarchies (Vehicle → Bike, Car, Truck) |
| **Polymorphism** | Swapping strategies, handling different types uniformly |
| **Composition** | Building complex objects (ParkingLot has Floors, Floor has Slots) |
| **Interfaces** | Enabling Strategy, Observer, Factory patterns |

---

## 🏛️ The 4 Pillars of OOP

### 1. Encapsulation 🔒

**"Bundle data and the methods that operate on it, and restrict direct access to internal state."**

The most practical OOP pillar for LLD — every entity you design should encapsulate its state.

#### ❌ Bad — Exposed internals

```java
class BankAccount {
    public double balance;  // Anyone can set this to -9999!
}

// Caller can do anything — no rules enforced
account.balance = -500;  // Invalid state, no validation
```

#### ✅ Good — Controlled access

```java
class BankAccount {
    private double balance;

    public BankAccount(double initialBalance) {
        if (initialBalance < 0) throw new IllegalArgumentException("Negative balance");
        this.balance = initialBalance;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Invalid amount");
        this.balance += amount;
    }

    public void withdraw(double amount) {
        if (amount > balance) throw new IllegalStateException("Insufficient funds");
        this.balance -= amount;
    }

    public double getBalance() { return balance; }
}
```

#### Why It Matters in LLD

- **ParkingSlot**: `occupied` and `vehicle` are private — only `park()` and `free()` can change them, enforcing rules
- **Booking**: `status` transitions (PENDING → CONFIRMED → CANCELLED) are controlled through methods, not direct field access
- **Room**: `bookingSchedule` is private — only `reserve()` and `release()` modify it after validation

#### Key Rules

| Rule | Example |
|---|---|
| Fields are `private` | `private boolean occupied;` |
| Access through methods | `slot.park(vehicle)` not `slot.occupied = true` |
| Validation in setters/methods | `if (amount <= 0) throw ...` |
| Expose only what's needed | Only getters for read-only data |

---

### 2. Abstraction 🎭

**"Hide complex implementation details and expose only what the caller needs to know."**

Abstraction lets you define *what* something does without specifying *how* it does it.

#### ❌ Bad — Caller knows implementation details

```java
class EmailNotifier {
    public void sendEmail(String to, String subject, String body) {
        // SMTP setup, connection, authentication...
        System.out.println("Connecting to SMTP server...");
        System.out.println("Authenticating...");
        System.out.println("Sending email to " + to);
    }
}

class SMSNotifier {
    public void sendSMS(String phoneNumber, String text) {
        // Twilio API setup, HTTP request...
        System.out.println("Connecting to Twilio API...");
        System.out.println("Sending SMS to " + phoneNumber);
    }
}

// Caller must know WHICH notifier and WHICH method to call
// Adding PushNotifier means changing all calling code
```

#### ✅ Good — Caller only knows the contract

```java
interface NotificationSender {
    void send(String recipient, String message);
}

class EmailNotifier implements NotificationSender {
    public void send(String recipient, String message) {
        // SMTP details hidden inside
        System.out.println("Email to " + recipient + ": " + message);
    }
}

class SMSNotifier implements NotificationSender {
    public void send(String recipient, String message) {
        // Twilio details hidden inside
        System.out.println("SMS to " + recipient + ": " + message);
    }
}

// Caller only knows "send" — doesn't care about Email vs SMS
NotificationSender sender = new EmailNotifier();
sender.send("user@example.com", "Hello!");
```

#### Why It Matters in LLD

- **PricingStrategy**: ParkingManager calls `calculateFee(ticket)` — doesn't know if it's hourly, dynamic, or flat-rate pricing
- **NotificationSender**: NotificationService calls `send()` — doesn't care if it's email, SMS, or push
- **PaymentProcessor**: OrderService calls `processPayment()` — doesn't know if it's credit card, UPI, or wallet

#### Abstraction vs Encapsulation

| | Encapsulation | Abstraction |
|---|---|---|
| **Focus** | Hiding *data* | Hiding *complexity* |
| **How** | Private fields + public methods | Interfaces + abstract classes |
| **Goal** | Protect internal state | Simplify usage for callers |
| **Example** | `private double balance` | `interface PaymentProcessor` |

---

### 3. Inheritance 🧬

**"A class (child) acquires the properties and behaviors of another class (parent)."**

Models "is-a" relationships. Use it sparingly — prefer composition for most LLD problems.

#### ❌ Bad — Deep/wrong inheritance

```java
// Forcing inheritance where it doesn't belong
class Bird {
    public void fly() { System.out.println("Flying"); }
    public void eat() { System.out.println("Eating"); }
}

class Penguin extends Bird {
    @Override
    public void fly() {
        throw new UnsupportedOperationException("Penguins can't fly!");
        // Violates Liskov Substitution Principle!
    }
}
```

#### ✅ Good — Shallow, meaningful inheritance

```java
// Base class with shared state and behavior
abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }
}

class Bike extends Vehicle {
    public Bike(String licensePlate) { super(licensePlate, VehicleType.BIKE); }
}

class Car extends Vehicle {
    public Car(String licensePlate) { super(licensePlate, VehicleType.CAR); }
}

class Truck extends Vehicle {
    public Truck(String licensePlate) { super(licensePlate, VehicleType.TRUCK); }
}
```

#### When to Use Inheritance in LLD

| Use Inheritance When | Example |
|---|---|
| Clear "is-a" relationship | Bike **is a** Vehicle |
| Shared state across subtypes | All vehicles have `licensePlate`, `type` |
| Subtypes don't break parent's contract | Any Vehicle can be parked |

| Avoid Inheritance When | Use Instead |
|---|---|
| "has-a" relationship | Composition (`ParkingLot` has `Floor`) |
| Only sharing behavior, not state | Interface (`PricingStrategy`) |
| Deep hierarchies (> 2 levels) | Composition + Interfaces |

---

### 4. Polymorphism 🔄

**"One interface, many implementations. The same method call behaves differently based on the actual object."**

The most powerful pillar for LLD — it's what makes Strategy, Factory, Observer, and most other patterns work.

#### Compile-Time Polymorphism (Method Overloading)

Same method name, different parameters. Resolved at compile time.

```java
class FeeCalculator {
    // Overloaded methods — different parameter lists
    public double calculate(int hours) {
        return hours * 10.0;
    }

    public double calculate(int hours, double multiplier) {
        return hours * 10.0 * multiplier;
    }

    public double calculate(int hours, double multiplier, double discount) {
        return (hours * 10.0 * multiplier) - discount;
    }
}
```

#### Runtime Polymorphism (Method Overriding) ⭐

Same method signature, different behavior based on actual object type. Resolved at runtime. **This is the one that matters for LLD.**

```java
interface PricingStrategy {
    double calculateFee(VehicleType type, long hours);
}

class HourlyPricing implements PricingStrategy {
    public double calculateFee(VehicleType type, long hours) {
        return switch (type) {
            case BIKE  -> hours * 10;
            case CAR   -> hours * 20;
            case TRUCK -> hours * 30;
        };
    }
}

class FlatRatePricing implements PricingStrategy {
    public double calculateFee(VehicleType type, long hours) {
        return switch (type) {
            case BIKE  -> 50;
            case CAR   -> 100;
            case TRUCK -> 200;
        };
    }
}

// ParkingManager doesn't know which strategy it's using — polymorphism!
class ParkingManager {
    private final PricingStrategy pricing;

    public ParkingManager(PricingStrategy pricing) {
        this.pricing = pricing;
    }

    public double calculateFee(VehicleType type, long hours) {
        return pricing.calculateFee(type, hours);  // Calls the right implementation
    }
}
```

#### Why It Matters in LLD

| Pattern | Polymorphism In Action |
|---|---|
| **Strategy** | `PricingStrategy` → HourlyPricing, FlatRatePricing, DynamicPricing |
| **Factory** | `VehicleFactory.create("car")` → returns Car, Bike, or Truck |
| **Observer** | `NotificationListener.onEvent()` → EmailListener, SMSListener |
| **State** | `BookingState.handle()` → PendingState, ConfirmedState, CancelledState |
| **Template Method** | `OrderProcessor.process()` → different steps for different order types |

---

## 🔧 Essential OOP Concepts

### 5. Composition vs Inheritance ⭐⭐⭐⭐⭐

**"Favor composition over inheritance"** — the most important design principle for LLD.

#### Inheritance = "is-a" | Composition = "has-a"

```java
// INHERITANCE: Dog IS-A Animal
class Animal { void eat() { ... } }
class Dog extends Animal { void bark() { ... } }

// COMPOSITION: Car HAS-A Engine
class Engine { void start() { ... } }
class Car {
    private final Engine engine;  // Car HAS an Engine
    public Car(Engine engine) { this.engine = engine; }
    public void start() { engine.start(); }
}
```

#### Why Composition Wins in LLD

```java
// INHERITANCE approach — rigid, hard to change
class ParkingLot extends Building {  // ParkingLot IS-A Building? Awkward.
    // Inherits walls, doors, windows... none of which matter
}

// COMPOSITION approach — flexible, clear ownership
class ParkingLot {
    private final String id;
    private final List<Floor> floors;       // HAS floors
    private final PricingStrategy pricing;  // HAS a pricing strategy (swappable!)

    public ParkingLot(String id, List<Floor> floors, PricingStrategy pricing) {
        this.id = id;
        this.floors = floors;
        this.pricing = pricing;
    }
}
```

#### Composition in Every LLD Problem

| Problem | Composition Used |
|---|---|
| **Parking Lot** | ParkingLot → Floor → ParkingSlot |
| **Movie Booking** | Theater → Screen → Seat, Show → Seat copies |
| **Hotel Booking** | Hotel → Room, BookingManager → RoomManager |
| **E-commerce** | Order → OrderItem → Product |
| **Notification** | NotificationService → List\<NotificationSender\> |

#### Decision Guide

```
Should I use inheritance or composition?

1. Is it a clear "is-a" with shared STATE?
   → Yes: Inheritance (Vehicle → Bike, Car, Truck)
   → No: Go to 2

2. Is it a "has-a" or "uses-a" relationship?
   → Yes: Composition (ParkingLot has Floors)

3. Do I need to swap behavior at runtime?
   → Yes: Composition + Interface (PricingStrategy)

4. Am I only sharing behavior (no shared state)?
   → Yes: Interface, not inheritance
```

---

### 6. Interfaces vs Abstract Classes 📋

Both provide abstraction, but serve different purposes.

#### Interface — A Contract (What to do)

```java
// Pure contract — no state, no implementation details
interface PricingStrategy {
    double calculateFee(VehicleType type, long hours);
}

// Any class can implement multiple interfaces
class DynamicPricing implements PricingStrategy, Serializable {
    public double calculateFee(VehicleType type, long hours) {
        double baseRate = getBaseRate(type);
        double surgeMultiplier = getSurgeMultiplier();
        return baseRate * hours * surgeMultiplier;
    }
}
```

#### Abstract Class — A Partial Implementation (How to do it, partially)

```java
// Shared state + partial implementation
abstract class Notification {
    private final String id;
    private final String recipient;
    private final String message;
    private NotificationStatus status;

    public Notification(String id, String recipient, String message) {
        this.id = id;
        this.recipient = recipient;
        this.message = message;
        this.status = NotificationStatus.PENDING;
    }

    // Concrete method — shared by all subclasses
    public void markSent() { this.status = NotificationStatus.SENT; }

    // Abstract method — each subclass implements differently
    public abstract void deliver();

    // Getters...
    public String getRecipient() { return recipient; }
    public String getMessage() { return message; }
}

class EmailNotification extends Notification {
    public EmailNotification(String id, String recipient, String message) {
        super(id, recipient, message);
    }

    @Override
    public void deliver() {
        System.out.println("Sending email to " + getRecipient());
        markSent();
    }
}
```

#### When to Use Which

| Use **Interface** When | Use **Abstract Class** When |
|---|---|
| Defining a pure contract | Sharing state (fields) across subtypes |
| Multiple implementations possible | Providing partial default behavior |
| Class needs to implement multiple contracts | Controlling construction (constructor logic) |
| No shared state needed | Template Method pattern |

| LLD Example | Choice | Why |
|---|---|---|
| PricingStrategy | Interface | Pure contract, no shared state |
| NotificationSender | Interface | Multiple unrelated implementations |
| Vehicle | Abstract Class | Shared state (licensePlate, type) |
| OrderProcessor | Abstract Class | Template Method with shared steps |

---

### 7. Enums 🏷️

Enums model fixed sets of constants with type safety. Used heavily in LLD for status, types, and categories.

#### Basic Enum — Type Safety

```java
// Instead of error-prone strings: "car", "CAR", "Car"
enum VehicleType { BIKE, CAR, TRUCK }
enum SlotType { BIKE, CAR, TRUCK }
enum BookingStatus { PENDING, CONFIRMED, CANCELLED }
enum SeatStatus { AVAILABLE, LOCKED, BOOKED }
```

#### Enum with State and Behavior

```java
enum VehicleType {
    BIKE(10.0),
    CAR(20.0),
    TRUCK(30.0);

    private final double hourlyRate;

    VehicleType(double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public double getHourlyRate() { return hourlyRate; }

    public double calculateFee(long hours) {
        return hourlyRate * hours;
    }
}

// Usage
double fee = VehicleType.CAR.calculateFee(3);  // 60.0
```

#### Enum for State Machines

```java
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
```

---

## 🗺️ OOP in LLD Problems — Quick Map

| OOP Concept | Parking Lot | Movie Booking | Hotel Booking | E-commerce |
|---|---|---|---|---|
| **Encapsulation** | ParkingSlot (private occupied, vehicle) | Seat (private status, lockedBy) | Room (private bookingSchedule) | Product (private stock) |
| **Abstraction** | PricingStrategy interface | BookingStrategy interface | PricingStrategy interface | PaymentProcessor interface |
| **Inheritance** | Vehicle → Bike, Car, Truck | — | — | — |
| **Polymorphism** | HourlyPricing vs FlatRatePricing | PessimisticLocking vs FineGrainedLocking | StandardPricing vs SeasonalPricing | CreditCardPayment vs UPIPayment |
| **Composition** | ParkingLot → Floor → Slot | Theater → Screen → Seat | Hotel → Room | Order → OrderItem → Product |
| **Interfaces** | PricingStrategy | — | PricingStrategy | PaymentProcessor, NotificationSender |
| **Enums** | VehicleType, SlotType | SeatStatus, BookingStatus | RoomType, BookingStatus | OrderStatus, PaymentStatus |

---

## ⚠️ Common Interview Mistakes

### 1. Using Inheritance Where Composition Fits

```java
// ❌ Wrong
class ParkingLot extends ArrayList<Floor> { }

// ✅ Right
class ParkingLot {
    private final List<Floor> floors;
}
```

### 2. Exposing Internal Collections

```java
// ❌ Wrong — caller can modify internal list
public List<Floor> getFloors() { return floors; }

// ✅ Right — return unmodifiable view
public List<Floor> getFloors() { return Collections.unmodifiableList(floors); }
```

### 3. Using Strings Instead of Enums

```java
// ❌ Wrong — typo-prone, no compile-time safety
String status = "pending";
if (status.equals("Pending")) { ... }  // Bug! Case mismatch

// ✅ Right — type-safe, IDE autocomplete
BookingStatus status = BookingStatus.PENDING;
if (status == BookingStatus.PENDING) { ... }
```

### 4. God Class (No Encapsulation/Abstraction)

```java
// ❌ Wrong — one class doing everything
class ParkingSystem {
    public void parkVehicle() { ... }
    public void calculateFee() { ... }
    public void sendNotification() { ... }
    public void processPayment() { ... }
    public void generateReport() { ... }
}

// ✅ Right — separate responsibilities
class ParkingManager { ... }
class PricingStrategy { ... }
class NotificationService { ... }
class PaymentProcessor { ... }
```

### 5. Deep Inheritance Hierarchies

```java
// ❌ Wrong — 4 levels deep, fragile
class Entity → class Vehicle → class MotorVehicle → class Car → class Sedan

// ✅ Right — flat, use interfaces for behavior
abstract class Vehicle { ... }
class Car extends Vehicle { ... }
interface Electric { void charge(); }
class ElectricCar extends Car implements Electric { ... }
```

---

## 📋 Quick Reference Card

### The 4 Pillars at a Glance

| Pillar | What | Why | LLD Signal |
|---|---|---|---|
| **Encapsulation** | Private fields + public methods | Protect state, enforce rules | Every entity class |
| **Abstraction** | Interfaces + abstract classes | Hide complexity, define contracts | Strategy, Observer, Factory |
| **Inheritance** | Parent → Child | Share state for "is-a" | Vehicle → Bike, Car, Truck |
| **Polymorphism** | Same method, different behavior | Swap implementations at runtime | All design patterns |

### Decision Cheat Sheet

```
Modeling a relationship?
├── "is-a" with shared state → Inheritance (abstract class)
├── "is-a" with only behavior → Interface
├── "has-a" → Composition (field)
└── "uses-a" → Dependency Injection (constructor param)

Defining a contract?
├── Pure behavior, no state → Interface
├── Shared state + partial behavior → Abstract Class
└── Fixed set of values → Enum

Choosing access level?
├── Internal state → private
├── Subclass needs access → protected
├── Part of the API → public
└── Package-level utility → default (no modifier)
```

### Interview One-Liners

- **Encapsulation**: "I keep fields private and expose controlled methods to enforce business rules."
- **Abstraction**: "I define interfaces so the manager class doesn't know the concrete implementation."
- **Inheritance**: "Vehicle is abstract with shared state; Bike, Car, Truck extend it."
- **Polymorphism**: "PricingStrategy is an interface — I can swap HourlyPricing for DynamicPricing without changing ParkingManager."
- **Composition over Inheritance**: "ParkingLot *has* Floors, it doesn't *extend* Floor. I compose objects, not inherit."

---

## 📖 Learning Path

```
Day 1: Encapsulation
  └── Understand private fields + controlled access → Run examples

Day 2: Abstraction
  └── Interfaces vs implementation hiding → Practice defining contracts

Day 3: Inheritance
  └── When to use, when to avoid → Model Vehicle hierarchy

Day 4: Polymorphism
  └── Method overriding + interface polymorphism → Implement Strategy pattern

Day 5: Composition & Interfaces vs Abstract Classes
  └── Refactor inheritance to composition → Practice decision-making

Day 6: Apply to LLD Problems
  └── Identify OOP concepts in Parking Lot, Movie Booking → Mock interview
```

---

**Next Steps**:
- Review [OOPConcepts.java](./OOPConcepts.java) for runnable examples of all concepts
- Move on to [SOLID Principles](../solidprinciples/) which build directly on these OOP foundations
- Apply these concepts in [LLD Problems](../problems/)
