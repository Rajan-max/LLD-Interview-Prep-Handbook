# 📘 LLD Interview Prep Handbook

A comprehensive, hands-on guide to **Low-Level Design (LLD)** interviews — covering SOLID principles, design patterns, concurrency controls, and real-world system design problems with complete Java implementations.

---

## 🎯 What's Inside

| Section | Description |
|---------|-------------|
| [OOP Concepts](#-oop-concepts) | 4 pillars + composition, interfaces vs abstract classes, enums with Java examples |
| [SOLID Principles](#-solid-principles) | 5 foundational design principles with bad vs good code examples |
| [Design Patterns](#-design-patterns) | 15 GoF patterns (Creational, Structural, Behavioral) with Java implementations |
| [Concurrency Controls](#-concurrency-controls) | 7 essential concurrency patterns for thread-safe system design |
| [LLD Problems](#-lld-problems) | 11 complete interview-style problems with solutions |
| [Interview Template](#-interview-template) | A time-boxed framework for delivering LLD interviews in 45 minutes |

---

## 📂 Project Structure

```
LLD-Interview Prep Handbook/
│
├── oops/                               # OOP Concepts
│   ├── OOPConcepts.java
│   └── README.md
│
├── solidprinciples/                    # SOLID Principles
│   ├── SingleResponsibilityPrinciple/
│   ├── OpenClosedPrinciple/
│   ├── LiskovSubstitutionPrinciple/
│   ├── InterfaceSegregationPrinciple/
│   └── DependencyInversionPrinciple/
│
├── designpatterns/                     # Design Patterns
│   ├── creational/
│   │   ├── SingletonDesignPattern/
│   │   ├── FactoryDesignPattern/
│   │   ├── AbstractFactoryDesignPattern/
│   │   └── BuilderDesignPattern/
│   ├── structural/
│   │   ├── AdapterDesignPattern/
│   │   ├── BridgeDesignPattern/
│   │   ├── CompositeDesignPattern/
│   │   ├── DecoratorDesignPattern/
│   │   ├── FacadeDesignPattern/
│   │   └── ProxyDesignPattern/
│   └── behavioral/
│       ├── ChainOfResponsibilityDesignPattern/
│       ├── ObserverDesignPattern/
│       ├── StateDesignPattern/
│       ├── StrategyDesignPattern/
│       └── TemplateMethodDesignPattern/
│
├── concurrency/                        # Concurrency Controls
│   ├── ConcurrencyPatterns.java
│   ├── QUICK_REFERENCE.md
│   └── PROBLEM_APPLICATION_GUIDE.md
│
├── problems/                           # LLD Interview Problems
│   ├── ParkingLotSystem/
│   ├── MovieBookingSystem/
│   ├── HotelBookingSystem/
│   ├── EcommerceSystem/
│   ├── NotificationSystem/
│   ├── VehicleRentalSystem/
│   ├── ElevatorSystem/
│   ├── RateLimiterSystem/
│   ├── FoodDeliverySystem/
│   ├── SplitwiseSystem/
│   └── LRUCacheSystem/
│
└── LLD_INTERVIEW_TEMPLATE.md           # Interview Delivery Framework
```

---

## 🧬 OOP Concepts

4 pillars of OOP + essential concepts, each with clear bad vs good examples and LLD-specific context.

| Concept | One-Liner | LLD Relevance |
|---------|-----------|---------------|
| **Encapsulation** | Private state + controlled access | Every entity (Slot, Room, Seat) hides internals |
| **Abstraction** | Hide complexity behind interfaces | PricingStrategy, NotificationSender contracts |
| **Inheritance** | Share state via "is-a" hierarchies | Vehicle → Bike, Car, Truck |
| **Polymorphism** | Same method, different behavior at runtime | Strategy, Factory, Observer patterns |
| **Composition** | Build objects with "has-a" relationships | ParkingLot → Floor → Slot |
| **Interfaces vs Abstract Classes** | Contract vs partial implementation | When to use which in LLD |
| **Enums** | Type-safe constants with behavior | Status machines, vehicle/slot types |

📁 [`oops/`](./oops/)

---

## 🧱 SOLID Principles

Each principle includes a README explaining the concept and a Java file demonstrating **bad code → refactored good code**.

| Principle | One-Liner | Key Question |
|-----------|-----------|--------------|
| **S** — Single Responsibility | One class, one reason to change | _"Does this class do more than one thing?"_ |
| **O** — Open/Closed | Open for extension, closed for modification | _"Can I add features without changing existing code?"_ |
| **L** — Liskov Substitution | Subtypes must be substitutable for their base types | _"Can I swap parent with child safely?"_ |
| **I** — Interface Segregation | Many small interfaces > one fat interface | _"Am I forcing unused method implementations?"_ |
| **D** — Dependency Inversion | Depend on abstractions, not concretions | _"Am I using `new` inside my class for dependencies?"_ |

📁 [`solidprinciples/`](./solidprinciples/)

---

## 🏗️ Design Patterns

15 patterns organized by category — each with a dedicated README and a self-contained Java implementation.

### Creational Patterns
| Pattern | Purpose |
|---------|---------|
| **Singleton** | Ensure a class has only one instance |
| **Factory Method** | Delegate object creation to subclasses |
| **Abstract Factory** | Create families of related objects |
| **Builder** | Construct complex objects step by step |

### Structural Patterns
| Pattern | Purpose |
|---------|---------|
| **Adapter** | Make incompatible interfaces work together |
| **Bridge** | Separate abstraction from implementation |
| **Composite** | Treat individual objects and compositions uniformly |
| **Decorator** | Add behavior dynamically without modifying the class |
| **Facade** | Provide a simplified interface to a complex subsystem |
| **Proxy** | Control access to an object |

### Behavioral Patterns
| Pattern | Purpose |
|---------|---------|
| **Chain of Responsibility** | Pass requests along a chain of handlers |
| **Observer** | Notify dependents when state changes |
| **State** | Alter behavior when internal state changes |
| **Strategy** | Swap algorithms at runtime |
| **Template Method** | Define algorithm skeleton, let subclasses fill in steps |

📁 [`designpatterns/`](./designpatterns/)

---

## 🔐 Concurrency Controls

7 essential concurrency patterns you need for LLD interviews, with runnable Java examples.

| Pattern | When to Use |
|---------|-------------|
| **Synchronized** | Simple mutual exclusion, low contention |
| **ReentrantLock** | Need timeout, tryLock, or fair ordering |
| **Fine-Grained Locking** | High contention on independent resources |
| **ReadWriteLock** | Read-heavy workloads (90% reads) |
| **Atomic Variables** | Lock-free counters and flags |
| **ConcurrentHashMap** | Thread-safe shared maps |
| **Double-Checked Locking** | Lazy initialization with minimal overhead |

📁 [`concurrency/`](./concurrency/)

---

## 💡 LLD Problems

11 complete interview-style problems — each with a `SOLUTION.md` (requirements → entities → class design → concurrency → implementation → testing → extensibility) and a runnable Java file.

| Problem | Key Concepts | Concurrency |
|---------|-------------|-------------|
| 🅿️ **Parking Lot System** | Slot-level locking, Strategy pattern, fee calculation | tryLock (non-blocking park), lock (blocking exit) |
| 🎬 **Movie Booking System** | Multi-seat booking, deadlock prevention, lock expiry | Fine-grained locking with sorted lock ordering |
| 🏨 **Hotel Booking System** | Date-range availability, two-phase booking (lock → confirm) | Room-level locking with timeout |
| 🛒 **E-commerce System** | Inventory management, order lifecycle, payment flow | Stock-level locking, atomic decrement |
| 🔔 **Notification System** | Observer pattern, multi-channel delivery | Thread-safe observer registration |
| 🚗 **Vehicle Rental System** | Fleet management, reservation conflicts | Vehicle-level locking |
| 🛗 **Elevator System** | SCAN algorithm, dispatch logic, simulation | None (single-threaded simulation) |
| 🚦 **Rate Limiter System** | Token Bucket, Sliding Window Log, Strategy + Factory patterns | None (single-threaded; per-key locking discussed) |
| 🍕 **Food Delivery System** | Order lifecycle, nearest-agent dispatch, delivery fee strategies | Agent-level locking (tryLock for assignment) |
| 💰 **Splitwise System** | Expense splitting (Equal/Exact/Percent), net balancing, settle-up | Balance-pair locking (synchronized per user pair) |
| 🗄️ **LRU Cache System** | HashMap + Doubly Linked List, O(1) get/put, sentinel nodes | synchronized get/put (method-level locking) |

📁 [`problems/`](./problems/)

---

## 📋 Interview Template

A battle-tested, time-boxed framework for delivering LLD interviews in ~45 minutes:

| Phase | Time | Focus |
|-------|------|-------|
| Requirements | ~5 min | Clarify prompt, write spec |
| Entities & Relationships | ~3 min | Identify core objects and ownership |
| Class Design | ~10 min | State + behavior for each class |
| Concurrency | ~5 min | Shared resources, race conditions, locking strategy |
| Implementation | ~10 min | Core methods + walkthrough |
| Testing | ~3 min | Key scenarios to verify |
| Extensibility | ~5 min | Interviewer-led follow-ups |

📄 [`LLD_INTERVIEW_TEMPLATE.md`](./LLD_INTERVIEW_TEMPLATE.md)

---

## 🚀 Getting Started

### Prerequisites
- Java 17+ (tested with Amazon Corretto 20)
- Any IDE (IntelliJ IDEA recommended)

### Run Any Example
Each Java file is self-contained with a `main` method:

```bash
# Compile and run a design pattern example
javac designpatterns/creational/SingletonDesignPattern/SingletonDesignPattern.java
java designpatterns.creational.SingletonDesignPattern.SingletonDesignPattern

# Compile and run a problem solution
javac problems/ParkingLotSystem/ParkingLotSystemComplete.java
java problems.ParkingLotSystem.ParkingLotSystemComplete
```

---

## 📖 Recommended Learning Path

```
Week 1: OOP Concepts + SOLID Principles
  └── OOP pillars → SOLID principles → Run examples → Identify violations in your own code

Week 2: Design Patterns
  └── Creational → Structural → Behavioral → Practice pattern selection

Week 3: Concurrency
  └── Core concepts → 7 patterns → Pattern selection guide → Write concurrent code

Week 4: LLD Problems
  └── Solve each problem using the Interview Template → Time yourself → Mock interviews
```

---

## 🤝 Contributing

Contributions are welcome! Feel free to:
- Add new LLD problems with solutions
- Add missing design patterns
- Improve existing explanations or code examples
- Fix bugs or typos

---

## ⭐ Star This Repo

If you found this helpful for your interview prep, consider giving it a ⭐ — it helps others discover it too!

---

## 📜 License

This project is open source and available under the [MIT License](LICENSE).
