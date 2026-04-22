package problems.ElevatorSystem;

import java.util.*;

/**
 * ELEVATOR SYSTEM - Complete Implementation
 *
 * Key Features:
 * - SCAN algorithm (sweep in one direction, reverse when done)
 * - Direction-aware stopping (only pick up passengers going your way)
 * - Nearest-elevator dispatch with direction penalty
 * - Discrete time-step simulation via step()
 * - No concurrency needed (single-threaded simulation)
 */

// ============================================================================
// ENUMS
// ============================================================================

enum Direction { UP, DOWN, IDLE }

enum RequestType { PICKUP_UP, PICKUP_DOWN, DESTINATION }

// ============================================================================
// REQUEST
// ============================================================================

class Request {
    private final int floor;
    private final RequestType type;

    public Request(int floor, RequestType type) {
        this.floor = floor;
        this.type = type;
    }

    public int getFloor() { return floor; }
    public RequestType getType() { return type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Request r)) return false;
        return floor == r.floor && type == r.type;
    }

    @Override
    public int hashCode() { return Objects.hash(floor, type); }

    @Override
    public String toString() { return type + "(" + floor + ")"; }
}

// ============================================================================
// ELEVATOR — SCAN Algorithm
// ============================================================================

/**
 * Single elevator car with SCAN movement logic.
 *
 * SCAN: sweep in current direction servicing matching stops,
 * reverse when no more stops ahead. Direction-aware stopping:
 * going UP only stops for PICKUP_UP and DESTINATION, not PICKUP_DOWN.
 */
class Elevator {
    static final int NUM_FLOORS = 10;

    private final int id;
    private int currentFloor;
    private Direction direction;
    private final Set<Request> requests;

    public Elevator(int id) {
        this.id = id;
        this.currentFloor = 0;
        this.direction = Direction.IDLE;
        this.requests = new HashSet<>();
    }

    public boolean addRequest(Request request) {
        if (request.getFloor() < 0 || request.getFloor() >= NUM_FLOORS) return false;
        if (request.getFloor() == currentFloor) return true;
        return requests.add(request);
    }

    /**
     * SCAN algorithm — one tick of movement.
     *
     * Case 1: No requests → IDLE
     * Case 2: IDLE with requests → pick direction toward nearest
     * Case 3: At a matching stop → remove request, don't move this tick
     * Case 4: No requests ahead → reverse, don't move this tick
     * Case 5: Move one floor in current direction
     */
    public void step() {
        if (requests.isEmpty()) {
            direction = Direction.IDLE;
            return;
        }

        if (direction == Direction.IDLE) {
            Request nearest = findNearestRequest();
            direction = (nearest.getFloor() > currentFloor) ? Direction.UP : Direction.DOWN;
        }

        // Direction-aware stop check
        RequestType pickupType = (direction == Direction.UP) ? RequestType.PICKUP_UP : RequestType.PICKUP_DOWN;
        Request pickup = new Request(currentFloor, pickupType);
        Request destination = new Request(currentFloor, RequestType.DESTINATION);

        if (requests.contains(pickup) || requests.contains(destination)) {
            requests.remove(pickup);
            requests.remove(destination);
            if (requests.isEmpty()) direction = Direction.IDLE;
            return;
        }

        if (!hasRequestsAhead()) {
            direction = (direction == Direction.UP) ? Direction.DOWN : Direction.UP;
            return;
        }

        currentFloor += (direction == Direction.UP) ? 1 : -1;
    }

    private Request findNearestRequest() {
        Request nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (Request r : requests) {
            int dist = Math.abs(r.getFloor() - currentFloor);
            if (dist < minDist || (dist == minDist && (nearest == null || r.getFloor() < nearest.getFloor()))) {
                minDist = dist;
                nearest = r;
            }
        }
        return nearest;
    }

    private boolean hasRequestsAhead() {
        for (Request r : requests) {
            if (direction == Direction.UP && r.getFloor() > currentFloor) return true;
            if (direction == Direction.DOWN && r.getFloor() < currentFloor) return true;
        }
        return false;
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }
    public int getRequestCount() { return requests.size(); }

    public String getStatus() {
        return "Elevator " + id + " [floor=" + currentFloor + ", dir=" + direction + ", stops=" + requests + "]";
    }
}

// ============================================================================
// ELEVATOR CONTROLLER — Dispatch + Simulation
// ============================================================================

class ElevatorController {
    private final List<Elevator> elevators;
    private final int numFloors;

    public ElevatorController(int numElevators, int numFloors) {
        this.numFloors = numFloors;
        this.elevators = new ArrayList<>();
        for (int i = 0; i < numElevators; i++) {
            elevators.add(new Elevator(i));
        }
    }

    /** Hall call — dispatches to best elevator */
    public boolean requestElevator(int floor, RequestType type) {
        if (floor < 0 || floor >= numFloors) return false;
        if (type == RequestType.DESTINATION) return false;

        Request request = new Request(floor, type);
        Elevator best = selectBestElevator(request);
        return best.addRequest(request);
    }

    /** Passenger inside elevator presses a floor button */
    public boolean selectDestination(int elevatorId, int floor) {
        if (elevatorId < 0 || elevatorId >= elevators.size()) return false;
        return elevators.get(elevatorId).addRequest(new Request(floor, RequestType.DESTINATION));
    }

    /** Advance all elevators one tick */
    public void step() {
        for (Elevator e : elevators) {
            e.step();
        }
    }

    /**
     * Dispatch: pick the best elevator for a hall call.
     * Prefers IDLE or moving-toward elevators. Penalizes moving-away.
     */
    private Elevator selectBestElevator(Request request) {
        Elevator best = null;
        int bestScore = Integer.MAX_VALUE;

        for (Elevator e : elevators) {
            int distance = Math.abs(e.getCurrentFloor() - request.getFloor());
            int score;

            if (e.getDirection() == Direction.IDLE) {
                score = distance;
            } else if (isMovingToward(e, request)) {
                score = distance;
            } else {
                score = distance + numFloors; // penalize moving away
            }

            if (score < bestScore || (score == bestScore
                    && (best == null || e.getCurrentFloor() < best.getCurrentFloor()))) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private boolean isMovingToward(Elevator e, Request request) {
        if (e.getDirection() == Direction.UP && request.getFloor() > e.getCurrentFloor()) return true;
        if (e.getDirection() == Direction.DOWN && request.getFloor() < e.getCurrentFloor()) return true;
        return false;
    }

    public Elevator getElevator(int id) { return elevators.get(id); }

    public void printStatus() {
        for (Elevator e : elevators) {
            System.out.println("  " + e.getStatus());
        }
    }
}

// ============================================================================
// DEMO AND TESTS
// ============================================================================

public class ElevatorSystemComplete {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   ELEVATOR SYSTEM — SCAN Algorithm Demo                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        testSingleElevatorScan();
        testDirectionAwareStopping();
        testIdleToMovement();
        testDispatchNearestElevator();
        testMultiElevatorScenario();
        testEdgeCases();
        testBoundaryFloors();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    // --- Test 1: Single elevator SCAN sweep ---
    static void testSingleElevatorScan() {
        System.out.println("Test 1: Single Elevator SCAN Sweep");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(1, 10);
        Elevator e = ctrl.getElevator(0);

        // Elevator at 0, add destinations 3, 7, 5 → should visit 3, 5, 7 (SCAN order)
        ctrl.selectDestination(0, 3);
        ctrl.selectDestination(0, 7);
        ctrl.selectDestination(0, 5);

        List<Integer> stoppedAt = new ArrayList<>();
        for (int tick = 0; tick < 20; tick++) {
            int prevFloor = e.getCurrentFloor();
            ctrl.step();
            // Detect stop: floor didn't change and we had requests
            if (e.getCurrentFloor() == prevFloor && tick > 0) {
                stoppedAt.add(prevFloor);
            }
            if (e.getDirection() == Direction.IDLE && e.getRequestCount() == 0 && tick > 0) break;
        }

        // Should stop at 3, 5, 7 in order
        check("SCAN visits floors in sweep order", stoppedAt.equals(List.of(3, 5, 7)));
        check("Elevator ends IDLE", e.getDirection() == Direction.IDLE);
        System.out.println();
    }

    // --- Test 2: Direction-aware stopping ---
    static void testDirectionAwareStopping() {
        System.out.println("Test 2: Direction-Aware Stopping");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(1, 10);
        Elevator e = ctrl.getElevator(0);

        // Move elevator to floor 4 first
        ctrl.selectDestination(0, 4);
        for (int i = 0; i < 10; i++) ctrl.step();

        // Now at floor 4, IDLE. Add: PICKUP_DOWN at 6, DESTINATION at 8
        e.addRequest(new Request(6, RequestType.PICKUP_DOWN));
        e.addRequest(new Request(8, RequestType.DESTINATION));

        List<Integer> stoppedAt = new ArrayList<>();
        for (int tick = 0; tick < 20; tick++) {
            int prevFloor = e.getCurrentFloor();
            ctrl.step();
            if (e.getCurrentFloor() == prevFloor) {
                stoppedAt.add(prevFloor);
            }
            if (e.getDirection() == Direction.IDLE && e.getRequestCount() == 0) break;
        }

        // Should go UP to 8 (skipping 6 — it's PICKUP_DOWN), then reverse DOWN to 6
        check("Skips PICKUP_DOWN while going UP, services on return", stoppedAt.contains(8) && stoppedAt.contains(6));
        check("Visits 8 before 6", stoppedAt.indexOf(8) < stoppedAt.indexOf(6));
        System.out.println();
    }

    // --- Test 3: IDLE to movement ---
    static void testIdleToMovement() {
        System.out.println("Test 3: IDLE → Movement on New Request");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(1, 10);
        Elevator e = ctrl.getElevator(0);

        check("Starts IDLE", e.getDirection() == Direction.IDLE);
        check("Starts at floor 0", e.getCurrentFloor() == 0);

        ctrl.selectDestination(0, 5);
        ctrl.step(); // should pick direction UP

        check("Direction becomes UP after request", e.getDirection() == Direction.UP);

        // Step until arrival
        for (int i = 0; i < 10; i++) ctrl.step();
        check("Arrives at floor 5", e.getCurrentFloor() == 5);
        check("Returns to IDLE", e.getDirection() == Direction.IDLE);
        System.out.println();
    }

    // --- Test 4: Dispatch to nearest elevator ---
    static void testDispatchNearestElevator() {
        System.out.println("Test 4: Dispatch to Nearest Elevator");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(3, 10);

        // Move elevators to different floors: E0→2, E1→5, E2→8
        ctrl.selectDestination(0, 2);
        ctrl.selectDestination(1, 5);
        ctrl.selectDestination(2, 8);
        for (int i = 0; i < 15; i++) ctrl.step();

        // Hall call at floor 6 UP → should go to E1 (at 5, distance=1) not E2 (at 8, distance=2)
        ctrl.requestElevator(6, RequestType.PICKUP_UP);

        check("E0 at floor 2", ctrl.getElevator(0).getCurrentFloor() == 2);
        check("E1 at floor 5", ctrl.getElevator(1).getCurrentFloor() == 5);
        check("E2 at floor 8", ctrl.getElevator(2).getCurrentFloor() == 8);
        check("E1 got the request (nearest)", ctrl.getElevator(1).getRequestCount() == 1);
        check("E0 has no new requests", ctrl.getElevator(0).getRequestCount() == 0);
        System.out.println();
    }

    // --- Test 5: Multi-elevator full scenario ---
    static void testMultiElevatorScenario() {
        System.out.println("Test 5: Multi-Elevator Full Scenario");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(3, 10);

        // Hall calls from different floors
        ctrl.requestElevator(3, RequestType.PICKUP_UP);
        ctrl.requestElevator(7, RequestType.PICKUP_DOWN);
        ctrl.requestElevator(5, RequestType.PICKUP_UP);

        System.out.println("  After dispatch:");
        ctrl.printStatus();

        // Run simulation
        for (int i = 0; i < 20; i++) ctrl.step();

        System.out.println("  After 20 ticks:");
        ctrl.printStatus();

        // All elevators should eventually go IDLE
        boolean allIdle = true;
        for (int i = 0; i < 3; i++) {
            if (ctrl.getElevator(i).getDirection() != Direction.IDLE) allIdle = false;
        }
        check("All elevators IDLE after servicing", allIdle);
        System.out.println();
    }

    // --- Test 6: Edge cases ---
    static void testEdgeCases() {
        System.out.println("Test 6: Edge Cases");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(3, 10);

        check("Invalid floor -1 rejected", !ctrl.requestElevator(-1, RequestType.PICKUP_UP));
        check("Invalid floor 10 rejected", !ctrl.requestElevator(10, RequestType.PICKUP_UP));
        check("DESTINATION type in hall call rejected", !ctrl.requestElevator(5, RequestType.DESTINATION));
        check("Invalid elevator ID rejected", !ctrl.selectDestination(5, 3));
        check("Current floor is no-op (returns true)", ctrl.getElevator(0).addRequest(new Request(0, RequestType.DESTINATION)));

        // Duplicate request
        ctrl.requestElevator(5, RequestType.PICKUP_UP);
        int countBefore = ctrl.getElevator(0).getRequestCount();
        ctrl.getElevator(0).addRequest(new Request(5, RequestType.PICKUP_UP));
        check("Duplicate request deduplicated", ctrl.getElevator(0).getRequestCount() == countBefore);
        System.out.println();
    }

    // --- Test 7: Boundary floors ---
    static void testBoundaryFloors() {
        System.out.println("Test 7: Boundary Floors (0 and 9)");
        System.out.println("-".repeat(56));

        ElevatorController ctrl = new ElevatorController(1, 10);

        // Go to floor 9
        ctrl.selectDestination(0, 9);
        for (int i = 0; i < 15; i++) ctrl.step();
        check("Reaches floor 9", ctrl.getElevator(0).getCurrentFloor() == 9);

        // Now request floor 0
        ctrl.selectDestination(0, 0);
        for (int i = 0; i < 15; i++) ctrl.step();
        check("Reaches floor 0 from 9", ctrl.getElevator(0).getCurrentFloor() == 0);
        check("Ends IDLE", ctrl.getElevator(0).getDirection() == Direction.IDLE);
        System.out.println();
    }

    // --- Helpers ---
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
