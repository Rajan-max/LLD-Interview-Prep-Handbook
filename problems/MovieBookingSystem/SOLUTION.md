# Movie Booking System - LLD Interview Solution 🎬
---

## 1) Requirements (~5 min)

**Prompt**: "Design a movie booking system where users can select seats and book tickets for shows."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | View shows, select seats, book multiple seats, confirm/cancel booking |
| **Primary capabilities** | Multi-seat booking? | Yes, a user can book multiple seats in one transaction |
| **Rules** | How is booking done? | Two-step: lock seats → confirm after payment |
| **Rules** | What if user doesn't pay? | Seats auto-unlock after 5 minutes (lock expiry) |
| **Error handling** | Two users pick same seat? | First one wins, second gets "seat unavailable" |
| **Error handling** | Deadlock risk? | Yes — two users booking overlapping seat sets in different order |
| **Scope** | Concurrent access? | Yes, 1000+ users per show simultaneously |

### Requirements

```
1. Users can view available seats for a show
2. Users can book multiple seats in a single transaction
3. System holds seats temporarily during payment (5-minute lock)
4. Users can confirm or cancel bookings
5. Prevent double-booking of seats
6. Prevent deadlocks when multiple users book overlapping seats

Out of Scope:
- User authentication/authorization
- Payment gateway integration
- Email/SMS notifications
- Dynamic pricing
- Seat recommendations / seat types
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Movie          (film details — immutable)
- Show           (specific screening — owns seats + per-seat locks)
- Seat           (individual seat — owns status, lockedBy, lockExpiry)
- Booking        (links user + show + seats, tracks status)
- BookingService (orchestrator — lock seats, confirm, cancel)

Relationships:
- Show → Seat (1:N) + per-seat ReentrantLocks
- Booking → Show, List<Seat> (references)
- BookingService → ShowRepository, BookingRepository
```

**Key decisions:**
- Locks live inside Show (not BookingService) — Show owns its seats, so it owns their locks
- Seat has a 3-state lifecycle: AVAILABLE → LOCKED → BOOKED (with lock expiry)
- Multi-seat booking requires **deadlock prevention** — sorted lock ordering
- This is the most concurrency-heavy LLD problem due to multi-resource locking

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "View available seats" | id, status (AVAILABLE/LOCKED/BOOKED) | Seat |
| "Book multiple seats" | seats map + per-seat locks | Show |
| "Hold seats during payment (5 min)" | lockedBy, lockExpiry | Seat |
| "Confirm or cancel" | id, userId, showId, seatIds, status | Booking |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Lock seats before payment | lockSeats(userId, showId, seatIds) → Booking | BookingService |
| Confirm after payment | confirmBooking(bookingId) → boolean | BookingService |
| Check if seat can be locked | lock(userId, minutes) → boolean | Seat |
| Transition seat to booked | book(userId) → boolean | Seat |
| Release expired locks | unlock() | Seat |

### Class Outlines

```
class Movie:
  - id, title, durationMinutes (immutable)

class Seat:                                 // Caller MUST hold lock
  - id: String
  - status: volatile SeatStatus (AVAILABLE → LOCKED → BOOKED)
  - lockedBy: volatile String
  - lockExpiry: volatile LocalDateTime

  + lock(userId, minutes) → bool
  + book(userId) → bool
  + unlock()
  + isLockExpired() → bool

class Show:
  - id: String
  - movie: Movie
  - seats: ConcurrentHashMap<String, Seat>
  - seatLocks: ConcurrentHashMap<String, ReentrantLock>

  + getSeat(seatId) → Seat
  + getSeatLock(seatId) → ReentrantLock

class Booking:
  - id: String (auto-generated)
  - userId, showId: String
  - seatIds: List<String>
  - status: volatile BookingStatus (PENDING → CONFIRMED / CANCELLED)

  + confirm()

class BookingService:                       // Orchestrator
  - showRepository: ShowRepository
  - bookingRepository: BookingRepository

  + lockSeats(userId, showId, seatIds) → Booking
  + confirmBooking(bookingId) → boolean
```

### Key Principle

- **Seat** owns its status transitions (lock, book, unlock) — but caller MUST hold the external lock
- **BookingService** owns the workflow (acquire locks → check → mutate → release)
- **Show** owns the lock registry (one ReentrantLock per seat)

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- Seat.status — multiple users trying to book the same seat simultaneously

**What can go wrong?**
- Double-booking: Two users book the same seat
- Deadlock: User A locks [Seat1, Seat2], User B locks [Seat2, Seat1] → circular wait
- Stale locks: User locks seats but never pays → seats stuck in LOCKED forever

**What's the locking strategy?**
- Seat-level locking (fine-grained). Each seat has its own ReentrantLock inside Show.

### Why Seat-Level Locking?

| Approach | Throughput | Decision |
|---|---|---|
| **Show-level lock** | Very low (serializes all bookings for a show) | ❌ Too coarse |
| **Seat-level lock** | High (parallel across different seats) | ✅ Chosen |

### Concurrency Strategy

```
Shared resource:
- Seat.status — multiple threads trying to lock/book same seat

Race condition prevented:
- Double-booking: tryLock + seat.lock() is atomic under lock

Deadlock prevention (CRITICAL for multi-seat booking):
- ALWAYS acquire locks in sorted seat ID order
- tryLock with 5s timeout — fail fast if can't acquire
- Release all acquired locks in reverse order on failure

Locking approach:
- Each seat has its own ReentrantLock(true) — fair lock, inside Show
- lockSeats: acquire all seat locks in sorted order → check + lock all → release all
- confirmBooking: acquire all seat locks in sorted order → book all → release all

Lock expiry:
- Seats locked for 5 minutes max
- Background thread cleans up expired locks every 30 seconds

Thread-safety:
- Seat: volatile fields + external lock (caller MUST hold lock)
- Show: ConcurrentHashMap for seats and locks
- Booking: volatile status + immutable fields
- Repositories: ConcurrentHashMap
```

**Why sorted lock ordering?**
Without it: User A locks [Seat1, Seat2], User B locks [Seat2, Seat1] → deadlock.
With sorting: Both lock [Seat1, Seat2] in same order → no circular wait.

**Why release in reverse order?**
Convention that pairs with sorted acquisition — ensures clean unwinding on failure.

---

## 5) Implementation (~10 min)

### Core Method: lockSeats

```java
public Booking lockSeats(String userId, String showId, List<String> seatIds) {
    Show show = showRepo.findById(showId);
    if (show == null) throw new IllegalArgumentException("Show not found");

    // DEADLOCK PREVENTION: Sort seat IDs
    List<String> sorted = new ArrayList<>(seatIds);
    Collections.sort(sorted);

    List<ReentrantLock> acquired = new ArrayList<>();

    try {
        // Acquire locks in sorted order
        for (String seatId : sorted) {
            ReentrantLock lock = show.getSeatLock(seatId);
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                releaseLocks(acquired);
                throw new RuntimeException("Timeout acquiring lock: " + seatId);
            }
            acquired.add(lock);
        }

        // All locks acquired — check and lock seats atomically
        for (String seatId : sorted) {
            if (!show.getSeat(seatId).lock(userId, 5)) {
                releaseLocks(acquired);
                throw new RuntimeException("Seat unavailable: " + seatId);
            }
        }

        // Create booking
        Booking booking = new Booking(userId, showId, sorted);
        bookingRepo.save(booking);
        return booking;

    } catch (InterruptedException e) {
        releaseLocks(acquired);
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted", e);
    } finally {
        releaseLocks(acquired);
    }
}
```

**What this demonstrates:**
- Sorted lock ordering prevents deadlock
- tryLock with 5s timeout prevents indefinite blocking
- Atomic check-and-lock for all seats under their respective locks
- Full rollback (release all locks) on any failure
- Lock released in finally — even if seat.lock() throws

### Core Method: confirmBooking

```java
public boolean confirmBooking(String bookingId) {
    Booking booking = bookingRepo.findById(bookingId);
    if (booking == null || booking.getStatus() != BookingStatus.PENDING) return false;

    Show show = showRepo.findById(booking.getShowId());
    List<String> sorted = new ArrayList<>(booking.getSeatIds());
    Collections.sort(sorted);

    List<ReentrantLock> acquired = new ArrayList<>();

    try {
        for (String seatId : sorted) {
            ReentrantLock lock = show.getSeatLock(seatId);
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                releaseLocks(acquired);
                return false;
            }
            acquired.add(lock);
        }

        for (String seatId : sorted) {
            if (!show.getSeat(seatId).book(booking.getUserId())) {
                releaseLocks(acquired);
                return false;
            }
        }

        booking.confirm();
        return true;

    } catch (InterruptedException e) {
        releaseLocks(acquired);
        Thread.currentThread().interrupt();
        return false;
    } finally {
        releaseLocks(acquired);
    }
}
```

### Helper: releaseLocks

```java
private void releaseLocks(List<ReentrantLock> locks) {
    for (int i = locks.size() - 1; i >= 0; i--) {
        locks.get(i).unlock();
    }
}
```

### Verification: Walk Through a Scenario

```
Scenario: Two users book overlapping seats — deadlock prevention in action

User A: lockSeats("A", "SHOW1", ["Seat3", "Seat1"])
  → Sorted: ["Seat1", "Seat3"]
  → Acquires lock on Seat1 ✓
  → Acquires lock on Seat3 ✓
  → Locks both seats
  → Returns Booking BKG-1001
  → Releases locks

User B: lockSeats("B", "SHOW1", ["Seat3", "Seat2"])
  → Sorted: ["Seat2", "Seat3"]
  → Acquires lock on Seat2 ✓
  → Acquires lock on Seat3 ✓ (after User A releases)
  → Locks both seats
  → Returns Booking BKG-1002
  → Releases locks

✓ No deadlock. Both users acquired locks in sorted order.
✓ No double-booking. Seat3 was available for User B after User A released the lock.
```

```
Scenario: Two users try to book the SAME seat

User A: lockSeats("A", "SHOW1", ["Seat1"])
  → Acquires lock on Seat1
  → seat.lock("A", 5) = true (AVAILABLE → LOCKED)
  → Returns Booking
  → Releases lock

User B: lockSeats("B", "SHOW1", ["Seat1"])
  → Acquires lock on Seat1 (after User A releases)
  → seat.lock("B", 5) = false (status is LOCKED, not AVAILABLE)
  → Throws "Seat unavailable: Seat1"

✓ No double-booking. Atomic check + lock under seat lock.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Lock 2 seats, confirm booking, verify seats are BOOKED
- Lock seats, let lock expire (5 min), verify seats return to AVAILABLE
- Confirm a non-existent booking → returns false
- Book a seat that's already LOCKED by another user → RuntimeException

**Concurrency tests:**
- **Different seats in parallel**: 10 users, each booking a different seat → all 10 succeed
- **Same seat contention**: 10 users, all booking Seat1 → exactly 1 succeeds, 9 fail
- **Deadlock prevention**: User A books [Seat1, Seat2], User B books [Seat2, Seat3] → no deadlock, both complete
- **Overlapping seat sets**: User A books [Seat1, Seat2], User B books [Seat2, Seat3] → one gets Seat2, other fails on it

**Edge cases:**
- Invalid show ID → IllegalArgumentException
- Invalid seat ID → null lock → handled gracefully
- Confirm already-confirmed booking → returns false
- Lock expiry cleanup doesn't interfere with active bookings

---

## 7) Extensibility (~5 min)

**"How would you add seat types (REGULAR, PREMIUM, VIP) with different pricing?"**
> "Add a SeatType enum with price. Add type field to Seat. Create a PricingStrategy interface that calculates total based on seat types. Inject it into BookingService — no changes to locking logic."

```java
interface PricingStrategy {
    double calculateTotal(List<Seat> seats);
}

class SeatTypePricing implements PricingStrategy {
    public double calculateTotal(List<Seat> seats) {
        return seats.stream().mapToDouble(s -> s.getType().getPrice()).sum();
    }
}
```

**"How would you add notifications on booking confirmation?"**
> "Observer pattern. BookingService fires a BookingConfirmed event. Notification channels (email, SMS, push) subscribe. Core booking logic doesn't change."

**"How would you handle lock expiry?"**
> "A ScheduledExecutorService runs every 30 seconds, iterates all seats, and calls unlock() on any seat where isLockExpired() is true — acquiring the seat lock first."

**"How would you scale to millions of concurrent users?"**
> "Partition shows across servers (each server owns a subset of shows). Within a server, the seat-level locking already maximizes parallelism. For distributed systems, replace ReentrantLock with Redis distributed locks."

**"How would you add a waitlist for sold-out shows?"**
> "Add a per-show waitlist queue. When a booking is cancelled or a lock expires, notify the first user in the waitlist. The seat locking mechanism stays the same."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **lockSeats** | O(n log n + n) | O(n) |
| **confirmBooking** | O(n log n + n) | O(1) |
| **isLockExpired** | O(1) per seat | O(1) |
| **cleanupExpiredLocks** | O(total seats) | O(1) |

*n = number of seats in a single booking. The sort is O(n log n), lock acquisition + seat mutation is O(n).*

---

**Implementation**: See [MovieBookingSystemComplete.java](./MovieBookingSystemComplete.java)
