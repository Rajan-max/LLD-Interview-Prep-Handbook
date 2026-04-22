# Vehicle Rental System - LLD Interview Solution 🚗
---

## 1) Requirements (~5 min)

**Prompt**: "Design a vehicle rental system where customers can search and rent vehicles."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | Search vehicles, reserve, confirm, return, cancel |
| **Primary capabilities** | Vehicle types? | Yes — BIKE, CAR, SUV, TRUCK with different daily rates |
| **Rules** | How is rental done? | Two-step: lock vehicle → confirm after payment |
| **Rules** | How is fee calculated? | Days × vehicle daily rate (support different strategies) |
| **Error handling** | What if no vehicles available? | Reject with error |
| **Error handling** | Double-rental attempt? | Second request fails gracefully |
| **Scope** | Concurrent access? | Yes, multiple customers renting simultaneously |

### Requirements

```
1. Support vehicle types (BIKE, CAR, SUV, TRUCK) with different daily rates
2. Search available vehicles by type and date range
3. Reserve a vehicle for a date range (two-step: lock → confirm)
4. Calculate rental fee based on days × vehicle rate
5. Return vehicle and release dates
6. Cancel rental and release dates
7. Support different pricing strategies (daily, weekly discount)
8. Prevent double-rental under concurrent access

Out of Scope:
- Multi-location management and transfers
- Insurance and damage tracking
- GPS tracking and fleet management
- Customer loyalty programs
- Late return penalties
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Vehicle         (rentable asset, owns date-based schedule)
- Customer        (person renting — immutable)
- Rental          (links customer + vehicle + dates, tracks status)
- VehicleManager  (resource registry + per-vehicle locks)
- RentalManager   (orchestrator — search, lock, confirm, return, cancel)
- PricingStrategy (calculates fee — swappable via Strategy pattern)

Relationships:
- RentalManager → VehicleManager (uses for vehicle lookup + locks)
- RentalManager → RentalRepository (persists rentals)
- RentalManager → PricingStrategy (calculates fees)
- Rental → Customer, Vehicle (references)
- Vehicle grouped by VehicleType
```

**Key decisions:**
- Locks live inside VehicleManager (not RentalManager) — VehicleManager owns vehicles, so it owns their locks
- VehicleManager handles resource registry + locks, RentalManager handles business logic (single responsibility)
- PricingStrategy is injected into RentalManager — swappable at construction time (Strategy pattern)
- Rental has 4 states: PENDING → CONFIRMED → RETURNED / CANCELLED

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Vehicle types with daily rates" | id, model, type (with baseDailyRate) | Vehicle, VehicleType enum |
| "Date-based availability" | rentalSchedule: Map<LocalDate, String> | Vehicle |
| "Reserve vehicle (lock → confirm)" | id, customer, vehicle, pickUp, dropOff, status, totalAmount | Rental |
| "Different pricing strategies" | calculateFee(type, days) | PricingStrategy interface |
| "Prevent double-rental" | per-vehicle ReentrantLock | VehicleManager |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Search and reserve a vehicle | searchAndLockVehicle(customer, type, pickUp, dropOff) → Rental | RentalManager |
| Confirm after payment | confirmRental(rentalId) → Rental | RentalManager |
| Return vehicle and release dates | returnVehicle(rentalId) → Rental | RentalManager |
| Cancel and release dates | cancelRental(rentalId) | RentalManager |
| Check if vehicle is available for dates | isAvailable(pickUp, dropOff) → bool | Vehicle |
| Reserve / release dates on vehicle | reserve(pickUp, dropOff, rentalId), release(pickUp, dropOff) | Vehicle |

### Class Outlines

```
class Vehicle:                              // Caller MUST hold lock
  - id: String
  - model: String
  - type: VehicleType
  - rentalSchedule: ConcurrentHashMap<LocalDate, String>

  + isAvailable(pickUp, dropOff) → bool
  + reserve(pickUp, dropOff, rentalId)
  + release(pickUp, dropOff)

class VehicleManager:
  - vehiclesByType: ConcurrentHashMap<VehicleType, List<Vehicle>>
  - vehicleLocks: ConcurrentHashMap<String, ReentrantLock>

  + addVehicle(vehicle)
  + getVehiclesByType(type) → List<Vehicle>
  + getVehicleLock(vehicleId) → ReentrantLock

class RentalManager:                        // Orchestrator
  - vehicleManager: VehicleManager
  - rentalRepository: RentalRepository
  - pricingStrategy: PricingStrategy

  + searchAndLockVehicle(customer, type, pickUp, dropOff) → Rental
  + confirmRental(rentalId) → Rental
  + returnVehicle(rentalId) → Rental
  + cancelRental(rentalId)

class Rental:
  - id: String (auto-generated)
  - customer: Customer
  - vehicle: Vehicle
  - pickUp, dropOff: LocalDate
  - totalAmount: double
  - status: volatile RentalStatus (PENDING → CONFIRMED → RETURNED / CANCELLED)

  + confirm(), returnVehicle(), cancel()

class Customer:
  - id, name, licenseNumber (immutable)

interface PricingStrategy:
  + calculateFee(VehicleType type, long days) → double

enum VehicleType:
  BIKE(50.0), CAR(100.0), SUV(150.0), TRUCK(200.0)
  - baseDailyRate
```

### Key Principle

- **Workflow rules** (can this rental be confirmed? returned?) → RentalManager (orchestrator)
- **Data rules** (is this date range available?) → Vehicle (owns the schedule)
- **Pricing rules** → PricingStrategy (swappable, injected)

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- Vehicle.rentalSchedule — multiple threads renting the same vehicle for overlapping dates

**What can go wrong?**
- Double-rental: Two threads rent the same vehicle for overlapping dates
- Lost update: Concurrent status changes (confirm/cancel/return) overwrite each other

**What's the locking strategy?**
- Vehicle-level locking. Each vehicle has its own ReentrantLock inside VehicleManager.

### Why Vehicle-Level Locking?

| Approach | Throughput | Decision |
|---|---|---|
| **Fleet-level lock** | Very low (serializes everything) | ❌ Too coarse |
| **Vehicle-level lock** | High (parallel across all vehicles) | ✅ Chosen |
| **Date-level lock** | Very high but complex deadlock risk | ❌ Over-engineered |

### Concurrency Strategy

```
Shared resource:
- Vehicle.rentalSchedule — multiple threads trying to rent same vehicle

Race condition prevented:
- Double-rental: tryLock + isAvailable + reserve is atomic under lock

Locking approach:
- Each vehicle has its own ReentrantLock(true) — fair lock, inside VehicleManager
- searchAndLockVehicle uses tryLock(5s) — skip to next vehicle on timeout
- confirmRental uses lock() (blocking) — must confirm this specific rental
- returnVehicle uses lock() (blocking) — must release this specific vehicle's dates
- cancelRental uses lock() (blocking) — must release this specific vehicle's dates

Thread-safety:
- Vehicle: ConcurrentHashMap schedule + external lock (caller MUST hold lock)
- Customer: immutable after creation
- Rental: volatile status + immutable fields
- RentalRepository: ConcurrentHashMap
- VehicleManager: ConcurrentHashMap for vehicles and locks
```

**Why tryLock(5s) for search (with timeout)?**
A customer doesn't care *which* vehicle they get — if one is locked, wait briefly then skip to the next. Timeout prevents indefinite blocking.

**Why lock() for confirm/return/cancel (blocking)?**
We *must* operate on the specific vehicle tied to the rental. We have to wait for the lock.

**No deadlock risk:**
Each operation locks only one vehicle at a time — no multi-resource locking, no lock ordering needed.

---

## 5) Implementation (~10 min)

### Core Method: searchAndLockVehicle

```java
public Rental searchAndLockVehicle(Customer customer, VehicleType type,
                                   LocalDate pickUp, LocalDate dropOff) {
    if (!pickUp.isBefore(dropOff))
        throw new IllegalArgumentException("Invalid dates");

    List<Vehicle> vehicles = vehicleManager.getVehiclesByType(type);

    for (Vehicle vehicle : vehicles) {
        ReentrantLock lock = vehicleManager.getVehicleLock(vehicle.getId());
        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    if (vehicle.isAvailable(pickUp, dropOff)) {
                        long days = ChronoUnit.DAYS.between(pickUp, dropOff);
                        double amount = pricingStrategy.calculateFee(type, days);

                        Rental rental = new Rental(customer, vehicle, pickUp, dropOff, amount);
                        vehicle.reserve(pickUp, dropOff, rental.getId());
                        rentalRepository.save(rental);
                        return rental;
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted");
        }
    }
    throw new IllegalStateException("No available " + type + " vehicles");
}
```

**What this demonstrates:**
- Atomic check-and-reserve (no gap between isAvailable and reserve)
- tryLock with 5s timeout (non-blocking, prevents indefinite wait)
- Fee calculated via injected PricingStrategy (Strategy pattern)
- Proper lock release in finally block
- Iterates through all vehicles of type (first available wins)

### Core Method: confirmRental

```java
public Rental confirmRental(String rentalId) {
    Rental rental = rentalRepository.findById(rentalId);
    if (rental == null)
        throw new IllegalArgumentException("Rental not found");

    ReentrantLock lock = vehicleManager.getVehicleLock(rental.getVehicle().getId());

    lock.lock();                                     // Blocking — must confirm this specific vehicle
    try {
        if (rental.getStatus() != RentalStatus.PENDING)
            throw new IllegalStateException("Rental cannot be confirmed");
        rental.confirm();
        return rental;
    } finally {
        lock.unlock();
    }
}
```

### Core Method: returnVehicle

```java
public Rental returnVehicle(String rentalId) {
    Rental rental = rentalRepository.findById(rentalId);
    if (rental == null)
        throw new IllegalArgumentException("Rental not found");

    Vehicle vehicle = rental.getVehicle();
    ReentrantLock lock = vehicleManager.getVehicleLock(vehicle.getId());

    lock.lock();                                     // Blocking — must release this specific vehicle
    try {
        if (rental.getStatus() != RentalStatus.CONFIRMED)
            throw new IllegalStateException("Rental not active");
        vehicle.release(rental.getPickUp(), rental.getDropOff());
        rental.returnVehicle();
        return rental;
    } finally {
        lock.unlock();
    }
}
```

### Core Method: cancelRental

```java
public void cancelRental(String rentalId) {
    Rental rental = rentalRepository.findById(rentalId);
    if (rental == null)
        throw new IllegalArgumentException("Rental not found");

    Vehicle vehicle = rental.getVehicle();
    ReentrantLock lock = vehicleManager.getVehicleLock(vehicle.getId());

    lock.lock();                                     // Blocking — must release this specific vehicle
    try {
        vehicle.release(rental.getPickUp(), rental.getDropOff());
        rental.cancel();
    } finally {
        lock.unlock();
    }
}
```

**Edge cases handled:**
- Invalid dates (pickUp not before dropOff) → IllegalArgumentException
- Rental not found → IllegalArgumentException
- Confirm non-PENDING rental → IllegalStateException
- Return non-CONFIRMED rental → IllegalStateException
- Cancel releases dates back to vehicle's schedule (makes vehicle available again)

### Verification: Walk Through a Scenario

```
Scenario: Two threads try to rent the same CAR for overlapping dates

Thread A: searchAndLockVehicle(Customer A, CAR, Jan 5-10)
  → Acquires lock on Vehicle V2
  → V2.isAvailable(Jan 5-10) = true
  → V2.reserve(Jan 5-10, "RNT-1001")
  → Returns Rental RNT-1001 ($500 = 5 days × $100)
  → Releases lock

Thread B: searchAndLockVehicle(Customer B, CAR, Jan 8-12)
  → Acquires lock on Vehicle V2 (after Thread A releases)
  → V2.isAvailable(Jan 8-12) = false (Jan 8,9 already reserved)
  → Moves to next CAR or throws "No available CAR vehicles"

✓ No double-rental. Atomic check + reserve under lock.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Search and lock a CAR, confirm it, verify status = CONFIRMED
- Calculate total: 5 days × $100 (CAR) = $500
- Return a confirmed rental, verify dates are released and status = RETURNED
- Weekly discount: 10 days CAR = 1 week × ($100 × 7 × 0.85) + 3 days × $100 = $895

**Concurrency tests:**
- **Single vehicle contention**: 10 threads, 1 CAR, same dates → only 1 succeeds
- **Parallel different vehicles**: 10 threads, 10 CARs, same dates → all 10 succeed
- **Overlapping date conflict**: Thread 1 rents Day 50-55, Thread 2 rents Day 53-58 on same vehicle → one fails
- **Return and re-rent**: Return vehicle, then another thread rents same vehicle/dates → both succeed

**Edge cases:**
- All vehicles of a type are rented for requested dates
- Invalid dates (dropOff before pickUp) → IllegalArgumentException
- Double return of same rental → IllegalStateException
- Cancel already-returned rental

---

## 7) Extensibility (~5 min)

**"How would you add new pricing strategies (surge, seasonal)?"**
> "PricingStrategy is already an interface. I'd implement SurgePricing or SeasonalPricing and inject it into RentalManager — no changes to existing code."

```java
class SurgePricing implements PricingStrategy {
    @Override
    public double calculateFee(VehicleType type, long days) {
        double multiplier = isHolidaySeason() ? 1.5 : 1.0;
        return type.baseDailyRate * days * multiplier;
    }
}
```

**"How would you add new vehicle types (LUXURY, ELECTRIC)?"**
> "Add to the VehicleType enum with its baseDailyRate. Create vehicles with that type. No changes to VehicleManager, RentalManager, or Vehicle."

**"How would you support multiple locations?"**
> "Each location would have its own VehicleManager. A top-level LocationManager routes requests to the right location's VehicleManager. The RentalManager and Vehicle classes don't change."

**"How would you add late return penalties?"**
> "Add an actualDropOff field to Rental. On returnVehicle, compare actualDropOff with dropOff. If late, calculate penalty via a PenaltyStrategy. The locking mechanism stays the same."

**"How would you scale to millions of vehicles?"**
> "The design already scales horizontally — vehicles are independent, locks are per-vehicle. For distributed systems, replace in-memory locks with Redis distributed locks and ConcurrentHashMap with a database. The class structure stays the same."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **searchAndLockVehicle** | O(V × D) worst case | O(D) |
| **confirmRental** | O(1) | O(1) |
| **returnVehicle** | O(D) | O(1) |
| **cancelRental** | O(D) | O(1) |

*V = vehicles of requested type, D = days in date range. With available-vehicle indexing: searchAndLockVehicle becomes O(D).*

---

**Implementation**: See [VehicleRentalSystemComplete.java](./VehicleRentalSystemComplete.java)
