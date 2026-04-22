# Hotel Booking System - LLD Interview Solution 🏨
---

## 1) Requirements (~5 min)

**Prompt**: "Design a hotel booking system where guests can search and reserve rooms."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | Search rooms, reserve room, confirm booking, cancel booking |
| **Primary capabilities** | Room types? | Yes — SINGLE, DOUBLE, SUITE with different pricing |
| **Rules** | How is booking done? | Two-step: lock room → confirm after payment |
| **Rules** | How is fee calculated? | Nights × room base price |
| **Error handling** | What if no rooms available? | Reject with error |
| **Error handling** | Double-booking attempt? | Second request fails gracefully |
| **Scope** | Concurrent access? | Yes, multiple guests booking simultaneously |

### Requirements

```
1. Support room types (SINGLE, DOUBLE, SUITE) with different pricing
2. Search available rooms by type and date range
3. Reserve a room for a date range (two-step: lock → confirm)
4. Calculate total based on nights × room rate
5. Cancel bookings and release dates
6. Prevent double-booking under concurrent access

Out of Scope:
- Payment gateway integration
- Check-in/check-out workflow
- Multi-hotel management
- Dynamic pricing / cancellation policies
- Guest loyalty programs
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Room           (bookable resource, owns date-based schedule)
- Guest          (person making the booking — immutable)
- Booking        (links guest + room + dates, tracks status)
- RoomManager    (resource registry + per-room locks)
- BookingManager (orchestrator — search, lock, confirm, cancel)

Relationships:
- BookingManager → RoomManager (uses for room lookup + locks)
- BookingManager → BookingRepository (persists bookings)
- Booking → Guest, Room (references)
- Room grouped by RoomType
```

**Key decisions:**
- Locks live inside RoomManager (not BookingManager) — RoomManager owns rooms, so it owns their locks
- RoomManager handles resource registry + locks, BookingManager handles business logic (single responsibility)
- Two-step flow (lock → confirm) mirrors real-world payment workflows — room is held while guest pays

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Room types with pricing" | id, type (with basePrice) | Room, RoomType enum |
| "Date-based availability" | bookingSchedule: Map<LocalDate, String> | Room |
| "Reserve room (lock → confirm)" | id, guest, room, checkIn, checkOut, status, totalAmount | Booking |
| "Prevent double-booking" | per-room ReentrantLock | RoomManager |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Search and reserve a room | SearchAndLockRoom(guest, type, checkIn, checkOut) → Booking | BookingManager |
| Confirm after payment | confirmBooking(bookingId) → Booking | BookingManager |
| Cancel and release dates | cancelBooking(bookingId) | BookingManager |
| Check if room is available for dates | isAvailable(checkIn, checkOut) → bool | Room |
| Reserve / release dates on room | reserve(checkIn, checkOut, bookingId), release(checkIn, checkOut) | Room |

### Class Outlines

```
class Room:                                 // Caller MUST hold lock
  - id: String
  - type: RoomType
  - bookingSchedule: ConcurrentHashMap<LocalDate, String>

  + isAvailable(checkIn, checkOut) → bool
  + reserve(checkIn, checkOut, bookingId)
  + release(checkIn, checkOut)

class RoomManager:
  - roomsByType: ConcurrentHashMap<RoomType, List<Room>>
  - roomLocks: ConcurrentHashMap<String, ReentrantLock>

  + addRoom(room)
  + getRoomByType(type) → List<Room>
  + getRoomLock(roomId) → ReentrantLock

class BookingManager:                       // Orchestrator
  - roomManager: RoomManager
  - bookingRepository: BookingRepository

  + SearchAndLockRoom(guest, type, checkIn, checkOut) → Booking
  + confirmBooking(bookingId) → Booking
  + cancelBooking(bookingId)

class Booking:
  - id: String (auto-generated)
  - guest: Guest
  - room: Room
  - checkIn, checkOut: LocalDate
  - totalAmount: double
  - status: volatile BookingStatus (PENDING → CONFIRMED / CANCELLED)

class Guest:
  - id, name (immutable)

enum RoomType:
  SINGLE(100.0, 1), DOUBLE(150.0, 2), SUITE(300.0, 4)
  - basePrice, maxOccupancy
```

### Key Principle

- **Workflow rules** (can this booking be confirmed?) → BookingManager (orchestrator)
- **Data rules** (is this date range available?) → Room (owns the schedule)

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- Room.bookingSchedule — multiple threads booking the same room for overlapping dates

**What can go wrong?**
- Double-booking: Two threads book the same room for overlapping dates
- Lost update: Concurrent status changes (confirm/cancel) overwrite each other

**What's the locking strategy?**
- Room-level locking. Each room has its own ReentrantLock inside RoomManager.

### Why Room-Level Locking?

| Approach | Throughput | Decision |
|---|---|---|
| **Hotel-level lock** | Very low (serializes everything) | ❌ Too coarse |
| **Room-level lock** | High (parallel across all rooms) | ✅ Chosen |
| **Date-level lock** | Very high but complex deadlock risk | ❌ Over-engineered |

### Concurrency Strategy

```
Shared resource:
- Room.bookingSchedule — multiple threads trying to book same room

Race condition prevented:
- Double-booking: tryLock + isAvailable + reserve is atomic under lock

Locking approach:
- Each room has its own ReentrantLock(true) — fair lock, inside RoomManager
- SearchAndLockRoom uses tryLock(5s) — skip to next room on timeout
- confirmBooking uses lock() (blocking) — must confirm this specific booking
- cancelBooking uses lock() (blocking) — must release this specific room's dates

Thread-safety:
- Room: ConcurrentHashMap schedule + external lock (caller MUST hold lock)
- Guest: immutable after creation
- Booking: volatile status + immutable fields
- BookingRepository: ConcurrentHashMap
- RoomManager: ConcurrentHashMap for rooms and locks
```

**Why tryLock(5s) for search (with timeout)?**
A guest doesn't care *which* room they get — if one is locked, wait briefly then skip to the next. Timeout prevents indefinite blocking.

**Why lock() for confirm/cancel (blocking)?**
We *must* operate on the specific room tied to the booking. We have to wait for the lock.

**No deadlock risk:**
Each operation locks only one room at a time — no multi-resource locking, no lock ordering needed.

---

## 5) Implementation (~10 min)

### Core Method: SearchAndLockRoom

```java
public Booking SearchAndLockRoom(Guest guest, RoomType type,
                                 LocalDate checkIn, LocalDate checkOut) {
    List<Room> rooms = roomManager.getRoomByType(type);

    for (Room room : rooms) {
        ReentrantLock lock = roomManager.getRoomLock(room.getId());
        try {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    if (room.isAvailable(checkIn, checkOut)) {
                        Booking booking = new Booking(guest, room, checkIn, checkOut);
                        room.reserve(checkIn, checkOut, booking.getId());
                        bookingRepository.save(booking);
                        return booking;
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
    throw new IllegalStateException("No available rooms");
}
```

**What this demonstrates:**
- Atomic check-and-reserve (no gap between isAvailable and reserve)
- tryLock with 5s timeout (non-blocking, prevents indefinite wait)
- Proper lock release in finally block
- Iterates through all rooms of type (first available wins)

### Core Method: confirmBooking

```java
public Booking confirmBooking(String bookingId) {
    Booking booking = bookingRepository.findById(bookingId);
    if (booking == null)
        throw new IllegalArgumentException("Booking not found");

    Room room = booking.getRoom();
    ReentrantLock lock = roomManager.getRoomLock(room.getId());

    lock.lock();                                     // Blocking — must confirm this specific room
    try {
        if (booking.getStatus() != BookingStatus.PENDING)
            throw new IllegalStateException("Booking cannot be confirmed");
        booking.confirm();
        return booking;
    } finally {
        lock.unlock();
    }
}
```

### Core Method: cancelBooking

```java
public void cancelBooking(String bookingId) {
    Booking booking = bookingRepository.findById(bookingId);
    if (booking == null)
        throw new IllegalArgumentException("Booking not found");

    Room room = booking.getRoom();
    ReentrantLock lock = roomManager.getRoomLock(room.getId());

    lock.lock();                                     // Blocking — must release this specific room
    try {
        room.release(booking.getCheckIn(), booking.getCheckOut());
        booking.cancel();
    } finally {
        lock.unlock();
    }
}
```

**Edge cases handled:**
- Booking not found → IllegalArgumentException
- Confirm non-PENDING booking → IllegalStateException
- Cancel releases dates back to room's schedule (makes room available again)

### Verification: Walk Through a Scenario

```
Scenario: Two threads try to book the same DOUBLE room for overlapping dates

Thread A: SearchAndLockRoom(Guest A, DOUBLE, Jan 5-10)
  → Acquires lock on Room R2
  → R2.isAvailable(Jan 5-10) = true
  → R2.reserve(Jan 5-10, "BK-1001")
  → Returns Booking BK-1001
  → Releases lock

Thread B: SearchAndLockRoom(Guest B, DOUBLE, Jan 8-12)
  → Acquires lock on Room R2 (after Thread A releases)
  → R2.isAvailable(Jan 8-12) = false (Jan 8,9 already reserved)
  → Moves to next DOUBLE room or throws "No available rooms"

✓ No double-booking. Atomic check + reserve under lock.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Search and lock a DOUBLE room, confirm it, verify status = CONFIRMED
- Calculate total: 3 nights × $150 (DOUBLE) = $450
- Cancel a confirmed booking, verify dates are released
- Confirm a non-existent booking → IllegalArgumentException

**Concurrency tests:**
- **Single room contention**: 10 threads, 1 SINGLE room, same dates → only 1 succeeds
- **Parallel different rooms**: 10 threads, 10 DOUBLE rooms, same dates → all 10 succeed
- **Overlapping date conflict**: Thread 1 books Day 50-55, Thread 2 books Day 53-58 on same room → one fails
- **Cancel and re-book**: Cancel existing booking, then another thread books same room/dates → both succeed

**Edge cases:**
- All rooms of a type are booked for requested dates
- Double cancellation of same booking
- Confirm already-cancelled booking → IllegalStateException

---

## 7) Extensibility (~5 min)

**"How would you add different pricing strategies?"**
> "I'd extract pricing into a Strategy interface. BookingManager takes a PricingStrategy in its constructor. To add seasonal or loyalty pricing, I implement a new strategy — no changes to existing code."

```java
interface PricingStrategy {
    double calculateFee(RoomType type, long nights);
}

class SeasonalPricing implements PricingStrategy {
    @Override
    public double calculateFee(RoomType type, long nights) {
        double multiplier = isPeakSeason() ? 1.5 : 1.0;
        return type.basePrice * nights * multiplier;
    }
}
```

**"How would you support multiple hotels?"**
> "Each hotel would have its own RoomManager. A top-level HotelManager routes requests to the right hotel's RoomManager. The BookingManager and Room classes don't change."

**"How would you add notifications?"**
> "I'd add an Observer pattern. When a booking is confirmed or cancelled, BookingManager fires an event. Notification channels (email, SMS, push) subscribe to these events. Core booking logic doesn't change."

**"How would you add new room types (e.g., PENTHOUSE)?"**
> "Add PENTHOUSE to the RoomType enum with its basePrice and maxOccupancy. Create rooms with that type. No changes to RoomManager, BookingManager, or Room."

**"How would you scale to millions of rooms?"**
> "The design already scales horizontally — rooms are independent, locks are per-room. For distributed systems, replace in-memory locks with Redis distributed locks and ConcurrentHashMap with a database. The class structure stays the same."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **SearchAndLockRoom** | O(R × D) worst case | O(D) |
| **confirmBooking** | O(1) | O(1) |
| **cancelBooking** | O(D) | O(1) |

*R = rooms of requested type, D = days in date range. With available-room indexing: SearchAndLockRoom becomes O(D).*

---

**Implementation**: See [HotelBookingSystemComplete.java](./HotelBookingSystemComplete.java)
