# Elevator System - LLD Interview Solution 🛗
---

## 1) Requirements (~5 min)

**Prompt**: "Design an elevator control system for a building."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | How many elevators and floors? | 3 elevators, 10 floors (0-9), fixed |
| **Primary capabilities** | How do users call an elevator? | Hall call from a floor with direction (UP or DOWN) |
| **Primary capabilities** | Can users select multiple floors inside? | Yes, multiple destination buttons |
| **Rules** | How does the system pick which elevator? | Nearest elevator (intelligent dispatch) |
| **Rules** | How do elevators move? | SCAN algorithm — sweep in one direction, reverse when no more stops ahead |
| **Rules** | Real-time or simulation? | Simulation — discrete time steps via `step()` |
| **Error handling** | Invalid floor number? | Reject, return false |
| **Error handling** | Request for current floor? | No-op, return true (already there) |
| **Scope** | Capacity, doors, emergency? | Out of scope |

### Requirements

```
1. System manages 3 elevators serving 10 floors (0-9)
2. Users can request an elevator from any floor (hall call with UP/DOWN direction)
3. System dispatches the best elevator for each hall call
4. Once inside, users can select one or more destination floors
5. Simulation runs in discrete time steps — step() advances all elevators one tick
6. Two types of stops:
   - Hall call: from a floor with direction (PICKUP_UP / PICKUP_DOWN)
   - Destination: from inside elevator (no direction)
7. Elevators use SCAN algorithm — sweep in current direction, reverse when done
8. Invalid requests rejected (return false), current-floor requests are no-ops

Out of Scope:
- Weight capacity and passenger limits
- Door open/close mechanics
- Emergency stop functionality
- Dynamic floor/elevator configuration
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Elevator           (one car — owns position, direction, request queue)
- Request            (a stop — floor + type: PICKUP_UP, PICKUP_DOWN, DESTINATION)
- ElevatorController (orchestrator — dispatches hall calls, advances time)

NOT entities (just primitives):
- Floor              (just an int — no state or behavior)

Relationships:
- ElevatorController → Elevator (1:N, owns 3 elevators)
- Elevator → Request (1:N, owns its stop queue as a Set)
- ElevatorController receives hall calls, dispatches to best Elevator
- Elevator receives destination requests directly from passengers
```

**Key decisions:**
- Floor is NOT a class — it's just an integer position. No state, no behavior.
- Request IS a class (not just an int) — we need floor + type to distinguish hall calls from destinations and to enable direction-aware stopping
- No concurrency — this is a single-threaded simulation with `step()`. No locks needed.
- Controller dispatches immediately (doesn't queue unassigned requests) — simpler for interviews

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "3 elevators serving 10 floors" | list of elevators | ElevatorController |
| "Elevator position and movement" | currentFloor, direction | Elevator |
| "Multiple destination floors" | set of requests (floor + type) | Elevator |
| "Hall call with direction" | floor, type (PICKUP_UP/DOWN/DESTINATION) | Request |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| User calls elevator from a floor | requestElevator(floor, type) → boolean | ElevatorController |
| Advance all elevators one tick | step() | ElevatorController |
| Add a stop to an elevator | addRequest(request) → boolean | Elevator |
| Execute one tick of movement | step() | Elevator |
| Dispatch logic — pick best elevator | selectBestElevator(request) → Elevator | ElevatorController |

### Class Outlines

```
enum Direction: UP, DOWN, IDLE

enum RequestType: PICKUP_UP, PICKUP_DOWN, DESTINATION

class Request:
  - floor: int
  - type: RequestType

  + equals/hashCode based on (floor, type)

class Elevator:
  - currentFloor: int (starts at 0)
  - direction: Direction (starts IDLE)
  - requests: Set<Request>

  + addRequest(request) → boolean
  + step()
  + getCurrentFloor() → int
  + getDirection() → Direction

class ElevatorController:
  - elevators: List<Elevator>
  - numFloors: int

  + requestElevator(floor, type) → boolean    // hall call entry point
  + selectDestination(elevatorId, floor) → boolean  // passenger presses floor inside
  + step()                                     // advance all elevators one tick
  + getElevatorStatus() → String               // for debugging/display
```

### Key Principle — SCAN Algorithm

The elevator sweeps in one direction, servicing all matching stops along the way. When no more stops exist ahead, it reverses. This is like a disk arm sweep — efficient, predictable, fair.

**Direction-aware stopping:**
- Going UP → stop for PICKUP_UP and DESTINATION requests, skip PICKUP_DOWN
- Going DOWN → stop for PICKUP_DOWN and DESTINATION requests, skip PICKUP_UP
- A PICKUP_DOWN request at floor 6 while going UP is NOT serviced until the elevator reverses

**Why SCAN over FIFO?**
- FIFO causes constant direction changes (go to 8, then 3, then 7 = 12 floors traveled)
- SCAN sweeps smoothly (go to 7, then 8, reverse to 3 = 8 floors traveled)

---

## 4) Concurrency Control (~5 min)

**This problem does NOT require concurrency control.**

The system is a single-threaded simulation. Time advances via explicit `step()` calls. There are no concurrent threads, no shared mutable state across threads, no race conditions.

```
Why no concurrency?
- Simulation model: caller controls time with step()
- Single-threaded: requests are added, then step() is called sequentially
- No parallel access: one thread adds requests and advances time

If this were a real elevator system (not a simulation):
- Hall calls would arrive concurrently → need synchronized dispatch
- step() and addRequest() would run on different threads → need lock or concurrent queue
- Solution: lock around requestElevator + step, OR use a thread-safe pending queue
  that step() drains at the start of each tick
```

**Skip this section in the interview** — mention "this is single-threaded simulation, no concurrency needed" and move on. Don't waste time on locks for a problem that doesn't need them.

---

## 5) Implementation (~10 min)

### Core Method: Elevator.step() — The SCAN Algorithm

```java
public void step() {
    // Case 1: Nothing to do
    if (requests.isEmpty()) {
        direction = Direction.IDLE;
        return;
    }

    // Case 2: If idle, pick direction toward nearest request
    if (direction == Direction.IDLE) {
        Request nearest = findNearestRequest();
        direction = (nearest.getFloor() > currentFloor) ? Direction.UP : Direction.DOWN;
    }

    // Case 3: Check if we should stop at current floor
    RequestType pickupType = (direction == Direction.UP) ? RequestType.PICKUP_UP : RequestType.PICKUP_DOWN;
    Request pickup = new Request(currentFloor, pickupType);
    Request destination = new Request(currentFloor, RequestType.DESTINATION);

    if (requests.contains(pickup) || requests.contains(destination)) {
        requests.remove(pickup);
        requests.remove(destination);
        if (requests.isEmpty()) direction = Direction.IDLE;
        return; // stopped this tick — don't move
    }

    // Case 4: Reverse if no requests ahead in current direction
    if (!hasRequestsAhead()) {
        direction = (direction == Direction.UP) ? Direction.DOWN : Direction.UP;
        return; // reversed this tick — don't move
    }

    // Case 5: Move one floor
    currentFloor += (direction == Direction.UP) ? 1 : -1;
}
```

**Why each case matters:**
- **Case 1**: Without this, an idle elevator would drift forever
- **Case 2**: Picks nearest request deterministically (lowest floor on tie) to establish direction
- **Case 3**: Direction-aware stopping — only picks up passengers going our way. A PICKUP_DOWN at floor 6 while going UP survives and is serviced on the return trip
- **Case 4**: Reversal without moving — next tick checks for stops in new direction
- **Case 5**: Move one floor per tick

**Boundary handling is implicit:**
- At floor 9 going UP → `hasRequestsAhead()` returns false → reverses to DOWN
- At floor 0 going DOWN → `hasRequestsAhead()` returns false → reverses to UP
- No hardcoded `if (currentFloor == 9)` checks needed

### Core Method: ElevatorController.requestElevator() — Dispatch

```java
public boolean requestElevator(int floor, RequestType type) {
    if (floor < 0 || floor >= numFloors) return false;
    if (type == RequestType.DESTINATION) return false; // hall calls only

    Request request = new Request(floor, type);
    Elevator best = selectBestElevator(request);
    return best.addRequest(request);
}
```

### Dispatch Strategy: selectBestElevator

```java
private Elevator selectBestElevator(Request request) {
    Elevator best = null;
    int bestScore = Integer.MAX_VALUE;

    for (Elevator e : elevators) {
        int distance = Math.abs(e.getCurrentFloor() - request.getFloor());

        // Prefer: 1) IDLE elevators, 2) elevators moving toward the request
        int score;
        if (e.getDirection() == Direction.IDLE) {
            score = distance;                          // idle — just distance
        } else if (isMovingToward(e, request)) {
            score = distance;                          // moving toward — same as idle
        } else {
            score = distance + numFloors;              // moving away — penalize
        }

        if (score < bestScore || (score == bestScore && (best == null
                || e.getCurrentFloor() < best.getCurrentFloor()))) {
            bestScore = score;
            best = e;
        }
    }
    return best;
}
```

### Helper: Elevator.addRequest

```java
public boolean addRequest(Request request) {
    if (request.getFloor() < 0 || request.getFloor() >= NUM_FLOORS) return false;
    if (request.getFloor() == currentFloor) return true; // already here — no-op
    return requests.add(request);
}
```

### Verification: Walk Through a Scenario

```
Setup: Elevator at floor 3, going UP, requests = {Request(5, PICKUP_UP), Request(7, DESTINATION)}

Tick 0: floor=3, dir=UP → not at stop, hasRequestsAhead=true → move up
Tick 1: floor=4, dir=UP → not at stop → move up
Tick 2: floor=5, dir=UP → STOP! Remove Request(5, PICKUP_UP). Still have requests → stay UP
Tick 3: floor=5, dir=UP → not at stop → move up
Tick 4: floor=6, dir=UP → not at stop → move up
Tick 5: floor=7, dir=UP → STOP! Remove Request(7, DESTINATION). No more requests → IDLE

✓ Elevator swept UP, stopped at both floors, went IDLE when done.
```

```
Direction-aware stopping: Elevator at floor 4, going UP, requests = {Request(6, PICKUP_DOWN), Request(8, DESTINATION)}

Tick 0: floor=4, dir=UP → move up
Tick 1: floor=5, dir=UP → move up
Tick 2: floor=6, dir=UP → check: PICKUP_UP at 6? No. DESTINATION at 6? No. → SKIP (PICKUP_DOWN not matched)
Tick 3: floor=7, dir=UP → move up
Tick 4: floor=8, dir=UP → STOP! Remove Request(8, DESTINATION). Still have Request(6, PICKUP_DOWN)
         → hasRequestsAhead(UP)? No → REVERSE to DOWN
Tick 5: floor=8, dir=DOWN → move down
Tick 6: floor=7, dir=DOWN → move down
Tick 7: floor=6, dir=DOWN → STOP! Remove Request(6, PICKUP_DOWN). No more requests → IDLE

✓ Elevator correctly PASSED floor 6 going UP (passenger wants DOWN), serviced it on return trip.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Single elevator, single request: request floor 5, step until arrival, verify IDLE
- Multiple destinations: add floors 3, 7, 5 → elevator visits 3, 5, 7 in order (SCAN)
- Direction-aware stopping: going UP, PICKUP_DOWN request is skipped until reversal
- Hall call dispatch: 3 elevators at different floors, verify nearest is chosen

**Edge cases:**
- Request floor -1 or floor 10 → returns false
- Request current floor → returns true (no-op)
- DESTINATION type in requestElevator → returns false (hall calls only)
- Elevator at floor 0 going DOWN → reverses to UP
- Elevator at floor 9 going UP → reverses to DOWN

**Multi-elevator tests:**
- 3 elevators, requests on different floors → all dispatched to nearest
- 2 requests for same floor, same type → deduplicated (Set)
- Idle elevator preferred over busy one at same distance

---

## 7) Extensibility (~5 min)

**"How would you add an express elevator that only stops at floors 0, 5, 9?"**
> "Add an `isExpress` flag and `expressFloors` set to Elevator. `addRequest` rejects non-express floors. Controller's dispatch logic prefers the express elevator for express floors when it's idle."

**"How would you add different scheduling algorithms (FIFO, LOOK, C-SCAN)?"**
> "Extract the movement logic into a `SchedulingStrategy` interface with a `step(elevator)` method. Inject it into Elevator. SCAN, FIFO, LOOK become different implementations — no changes to ElevatorController."

```java
interface SchedulingStrategy {
    void step(Elevator elevator);
}

class ScanStrategy implements SchedulingStrategy { ... }
class FifoStrategy implements SchedulingStrategy { ... }
```

**"How would you handle concurrent hall calls in a real system?"**
> "Two options: (1) Lock around `requestElevator` and `step` — simple but they block each other. (2) `addRequest` writes to a thread-safe pending queue, `step()` drains it at the start of each tick — no contention. Option 2 is better for throughput."

**"How would you add undo (cancel a floor request)?"**
> "Add `removeRequest(request)` to Elevator that removes from the set. If the elevator hasn't arrived yet, it skips the floor. If already past it, no-op. Core `step()` logic doesn't change."

**"How would you scale to 50 floors and 10 elevators?"**
> "The design already supports it — just change the constants. For smarter dispatch with many elevators, replace `selectBestElevator` with a more sophisticated strategy that considers elevator load, direction alignment, and estimated arrival time."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **requestElevator** | O(E) | O(1) |
| **selectBestElevator** | O(E) | O(1) |
| **Elevator.step** | O(R) | O(1) |
| **Elevator.addRequest** | O(1) | O(1) |
| **ElevatorController.step** | O(E × R) | O(1) |

*E = number of elevators, R = number of requests per elevator.*

---

**Implementation**: See [ElevatorSystemComplete.java](./ElevatorSystemComplete.java)
