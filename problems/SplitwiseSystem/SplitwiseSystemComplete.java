package problems.SplitwiseSystem;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * SPLITWISE SYSTEM - Complete Implementation
 *
 * Key Features:
 * - Strategy pattern for split types (Equal, Exact, Percent)
 * - Net balance tracking between user pairs
 * - Group expense support
 * - Settle-up with validation
 * - Thread-safe balance updates via synchronized inner maps
 */

// ============================================================================
// ENUMS
// ============================================================================

enum SplitType { EQUAL, EXACT, PERCENT }

// ============================================================================
// MODELS
// ============================================================================

class User {
    private final String id;
    private final String name;
    private final String email;

    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() { return id; }
    public String getName() { return name; }
}

class Group {
    private final String id;
    private final String name;
    private final Set<String> memberIds = ConcurrentHashMap.newKeySet();

    public Group(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addMember(String userId) { memberIds.add(userId); }
    public String getId() { return id; }
    public String getName() { return name; }
    public Set<String> getMemberIds() { return Collections.unmodifiableSet(memberIds); }
}

class Split {
    private final String userId;
    private final double amount;

    public Split(String userId, double amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public String getUserId() { return userId; }
    public double getAmount() { return amount; }
}

class Expense {
    private static final AtomicInteger idGen = new AtomicInteger(1000);

    private final String id;
    private final String paidBy;
    private final double amount;
    private final List<Split> splits;
    private final String groupId; // nullable

    public Expense(String paidBy, double amount, List<Split> splits, String groupId) {
        this.id = "EXP-" + idGen.getAndIncrement();
        this.paidBy = paidBy;
        this.amount = amount;
        this.splits = List.copyOf(splits);
        this.groupId = groupId;
    }

    public String getId() { return id; }
    public String getPaidBy() { return paidBy; }
    public double getAmount() { return amount; }
    public List<Split> getSplits() { return splits; }
    public String getGroupId() { return groupId; }
}

// ============================================================================
// STRATEGY PATTERN — Split Calculation
// ============================================================================

interface SplitStrategy {
    List<Split> calculateSplits(double amount, List<String> participantIds,
                                Map<String, Double> params);
    boolean validate(double amount, List<String> participantIds,
                     Map<String, Double> params);
}

class EqualSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculateSplits(double amount, List<String> participantIds,
                                       Map<String, Double> params) {
        double share = Math.round(amount / participantIds.size() * 100.0) / 100.0;
        return participantIds.stream().map(id -> new Split(id, share)).toList();
    }

    @Override
    public boolean validate(double amount, List<String> participantIds,
                            Map<String, Double> params) {
        return amount > 0 && !participantIds.isEmpty();
    }
}

class ExactSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculateSplits(double amount, List<String> participantIds,
                                       Map<String, Double> params) {
        return participantIds.stream()
                .map(id -> new Split(id, params.getOrDefault(id, 0.0)))
                .toList();
    }

    @Override
    public boolean validate(double amount, List<String> participantIds,
                            Map<String, Double> params) {
        if (params == null || params.isEmpty()) return false;
        double total = participantIds.stream()
                .mapToDouble(id -> params.getOrDefault(id, 0.0)).sum();
        return Math.abs(total - amount) < 0.01;
    }
}

class PercentSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculateSplits(double amount, List<String> participantIds,
                                       Map<String, Double> params) {
        return participantIds.stream()
                .map(id -> new Split(id,
                        Math.round(amount * params.getOrDefault(id, 0.0) / 100.0 * 100.0) / 100.0))
                .toList();
    }

    @Override
    public boolean validate(double amount, List<String> participantIds,
                            Map<String, Double> params) {
        if (params == null || params.isEmpty()) return false;
        double totalPercent = participantIds.stream()
                .mapToDouble(id -> params.getOrDefault(id, 0.0)).sum();
        return Math.abs(totalPercent - 100.0) < 0.01;
    }
}

// ============================================================================
// BALANCE SHEET — Net Balance Tracking
// ============================================================================

/**
 * Thread-safe balance tracking between user pairs.
 * balances[A][B] > 0 means A owes B that amount.
 */
class BalanceSheet {
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> balances =
            new ConcurrentHashMap<>();

    /** fromUser owes toUser `amount` more. Nets against reverse debt. */
    public void updateBalance(String fromUser, String toUser, double amount) {
        if (fromUser.equals(toUser) || amount == 0) return;

        ConcurrentHashMap<String, Double> fromMap =
                balances.computeIfAbsent(fromUser, k -> new ConcurrentHashMap<>());

        synchronized (getCanonicalLock(fromUser, toUser)) {
            double fromOwes = fromMap.getOrDefault(toUser, 0.0);
            ConcurrentHashMap<String, Double> toMap =
                    balances.computeIfAbsent(toUser, k -> new ConcurrentHashMap<>());
            double toOwes = toMap.getOrDefault(fromUser, 0.0);

            if (toOwes > 0) {
                double net = amount - toOwes;
                if (net > 0) {
                    fromMap.put(toUser, fromOwes + net);
                    toMap.remove(fromUser);
                } else if (net < 0) {
                    fromMap.remove(toUser);
                    toMap.put(fromUser, -net);
                } else {
                    fromMap.remove(toUser);
                    toMap.remove(fromUser);
                }
            } else {
                fromMap.put(toUser, fromOwes + amount);
            }
        }
    }

    /** Returns how much userA owes userB. Positive = A owes B. */
    public double getBalance(String userA, String userB) {
        double aOwesB = balances.getOrDefault(userA, new ConcurrentHashMap<>())
                .getOrDefault(userB, 0.0);
        double bOwesA = balances.getOrDefault(userB, new ConcurrentHashMap<>())
                .getOrDefault(userA, 0.0);
        return aOwesB - bOwesA;
    }

    /** All balances for a user. Positive = this user owes them. Negative = they owe this user. */
    public Map<String, Double> getUserBalances(String userId) {
        Map<String, Double> result = new HashMap<>();

        // What userId owes others
        ConcurrentHashMap<String, Double> owes = balances.getOrDefault(userId, new ConcurrentHashMap<>());
        for (var entry : owes.entrySet()) {
            result.merge(entry.getKey(), entry.getValue(), Double::sum);
        }

        // What others owe userId (negative = they owe me)
        for (var entry : balances.entrySet()) {
            if (!entry.getKey().equals(userId)) {
                double theyOweMe = entry.getValue().getOrDefault(userId, 0.0);
                if (theyOweMe > 0) {
                    result.merge(entry.getKey(), -theyOweMe, Double::sum);
                }
            }
        }

        // Remove zero entries
        result.entrySet().removeIf(e -> Math.abs(e.getValue()) < 0.01);
        return result;
    }

    // Canonical lock object per user pair to prevent deadlocks
    private final ConcurrentHashMap<String, Object> pairLocks = new ConcurrentHashMap<>();

    private Object getCanonicalLock(String a, String b) {
        String key = a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
        return pairLocks.computeIfAbsent(key, k -> new Object());
    }
}

// ============================================================================
// SERVICE — Orchestrator
// ============================================================================

class ExpenseService {
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Group> groups = new ConcurrentHashMap<>();
    private final List<Expense> expenses = Collections.synchronizedList(new ArrayList<>());
    private final BalanceSheet balanceSheet = new BalanceSheet();
    private final Map<SplitType, SplitStrategy> strategies;

    public ExpenseService() {
        strategies = Map.of(
                SplitType.EQUAL, new EqualSplitStrategy(),
                SplitType.EXACT, new ExactSplitStrategy(),
                SplitType.PERCENT, new PercentSplitStrategy()
        );
    }

    public void addUser(User user) { users.put(user.getId(), user); }

    public void createGroup(String groupId, String name, List<String> memberIds) {
        Group group = new Group(groupId, name);
        for (String id : memberIds) {
            if (!users.containsKey(id)) throw new IllegalArgumentException("User not found: " + id);
            group.addMember(id);
        }
        groups.put(groupId, group);
    }

    public Expense addExpense(String paidBy, double amount, List<String> participantIds,
                              SplitType splitType, Map<String, Double> params) {
        validateUsers(paidBy, participantIds);

        SplitStrategy strategy = strategies.get(splitType);
        if (!strategy.validate(amount, participantIds, params))
            throw new IllegalArgumentException("Invalid split: amounts don't match total");

        List<Split> splits = strategy.calculateSplits(amount, participantIds, params);
        Expense expense = new Expense(paidBy, amount, splits, null);
        expenses.add(expense);

        for (Split split : splits) {
            if (!split.getUserId().equals(paidBy)) {
                balanceSheet.updateBalance(split.getUserId(), paidBy, split.getAmount());
            }
        }
        return expense;
    }

    public Expense addGroupExpense(String paidBy, String groupId, double amount,
                                   SplitType splitType, Map<String, Double> params) {
        Group group = groups.get(groupId);
        if (group == null) throw new IllegalArgumentException("Group not found");
        if (!group.getMemberIds().contains(paidBy))
            throw new IllegalArgumentException("Payer not in group");

        List<String> participantIds = new ArrayList<>(group.getMemberIds());
        validateUsers(paidBy, participantIds);

        SplitStrategy strategy = strategies.get(splitType);
        if (!strategy.validate(amount, participantIds, params))
            throw new IllegalArgumentException("Invalid split");

        List<Split> splits = strategy.calculateSplits(amount, participantIds, params);
        Expense expense = new Expense(paidBy, amount, splits, groupId);
        expenses.add(expense);

        for (Split split : splits) {
            if (!split.getUserId().equals(paidBy)) {
                balanceSheet.updateBalance(split.getUserId(), paidBy, split.getAmount());
            }
        }
        return expense;
    }

    public void settleUp(String fromUser, String toUser, double amount) {
        if (!users.containsKey(fromUser) || !users.containsKey(toUser))
            throw new IllegalArgumentException("User not found");
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");

        double owed = balanceSheet.getBalance(fromUser, toUser);
        if (owed <= 0)
            throw new IllegalStateException(fromUser + " doesn't owe " + toUser);
        if (amount > owed + 0.01)
            throw new IllegalStateException("Cannot settle more than owed: $" + String.format("%.2f", owed));

        balanceSheet.updateBalance(toUser, fromUser, amount);
    }

    public Map<String, Double> getBalances(String userId) {
        return balanceSheet.getUserBalances(userId);
    }

    public Map<String, Map<String, Double>> getGroupBalances(String groupId) {
        Group group = groups.get(groupId);
        if (group == null) throw new IllegalArgumentException("Group not found");

        Map<String, Map<String, Double>> result = new HashMap<>();
        for (String memberId : group.getMemberIds()) {
            Map<String, Double> memberBalances = balanceSheet.getUserBalances(memberId).entrySet()
                    .stream()
                    .filter(e -> group.getMemberIds().contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!memberBalances.isEmpty()) result.put(memberId, memberBalances);
        }
        return result;
    }

    private void validateUsers(String paidBy, List<String> participantIds) {
        if (!users.containsKey(paidBy))
            throw new IllegalArgumentException("Payer not found");
        for (String id : participantIds)
            if (!users.containsKey(id))
                throw new IllegalArgumentException("Participant not found: " + id);
    }
}

// ============================================================================
// DEMO AND TESTS
// ============================================================================

public class SplitwiseSystemComplete {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   SPLITWISE SYSTEM — Expense Sharing Demo                ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        testEqualSplit();
        testExactSplit();
        testPercentSplit();
        testNetBalancing();
        testSettleUp();
        testPartialSettle();
        testGroupExpense();
        testValidation();
        testConcurrentExpenses();
        testEdgeCases();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    static ExpenseService setupService() {
        ExpenseService svc = new ExpenseService();
        svc.addUser(new User("A", "Alice", "alice@test.com"));
        svc.addUser(new User("B", "Bob", "bob@test.com"));
        svc.addUser(new User("C", "Charlie", "charlie@test.com"));
        svc.addUser(new User("D", "Diana", "diana@test.com"));
        return svc;
    }

    // --- Test 1: Equal split ---
    static void testEqualSplit() {
        System.out.println("Test 1: Equal Split");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        Expense exp = svc.addExpense("A", 300, List.of("A", "B", "C"),
                SplitType.EQUAL, null);

        check("Expense created", exp != null);
        check("3 splits", exp.getSplits().size() == 3);

        Map<String, Double> aBalances = svc.getBalances("A");
        // B owes A $100, C owes A $100 → A's view: B=-100, C=-100 (they owe me)
        check("B owes A $100", Math.abs(aBalances.getOrDefault("B", 0.0) - (-100.0)) < 0.01);
        check("C owes A $100", Math.abs(aBalances.getOrDefault("C", 0.0) - (-100.0)) < 0.01);
        System.out.println();
    }

    // --- Test 2: Exact split ---
    static void testExactSplit() {
        System.out.println("Test 2: Exact Split");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        svc.addExpense("A", 100, List.of("A", "B", "C"),
                SplitType.EXACT, Map.of("A", 50.0, "B", 30.0, "C", 20.0));

        Map<String, Double> aBalances = svc.getBalances("A");
        check("B owes A $30", Math.abs(aBalances.getOrDefault("B", 0.0) - (-30.0)) < 0.01);
        check("C owes A $20", Math.abs(aBalances.getOrDefault("C", 0.0) - (-20.0)) < 0.01);
        System.out.println();
    }

    // --- Test 3: Percent split ---
    static void testPercentSplit() {
        System.out.println("Test 3: Percent Split");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        svc.addExpense("A", 200, List.of("A", "B", "C"),
                SplitType.PERCENT, Map.of("A", 50.0, "B", 30.0, "C", 20.0));

        Map<String, Double> aBalances = svc.getBalances("A");
        // B's share = 200 * 30% = $60, C's share = 200 * 20% = $40
        check("B owes A $60", Math.abs(aBalances.getOrDefault("B", 0.0) - (-60.0)) < 0.01);
        check("C owes A $40", Math.abs(aBalances.getOrDefault("C", 0.0) - (-40.0)) < 0.01);
        System.out.println();
    }

    // --- Test 4: Net balancing (two-way debts) ---
    static void testNetBalancing() {
        System.out.println("Test 4: Net Balancing (Two-Way Debts)");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        // A pays $300 split equally among A, B, C → B owes A $100, C owes A $100
        svc.addExpense("A", 300, List.of("A", "B", "C"), SplitType.EQUAL, null);

        // B pays $150 split equally among A, B, C → A owes B $50, C owes B $50
        svc.addExpense("B", 150, List.of("A", "B", "C"), SplitType.EQUAL, null);

        // Net: B owes A $100 - $50 = $50
        Map<String, Double> bBalances = svc.getBalances("B");
        check("Net: B owes A $50 (100 - 50)", Math.abs(bBalances.getOrDefault("A", 0.0) - 50.0) < 0.01);

        // C owes A $100, C owes B $50
        Map<String, Double> cBalances = svc.getBalances("C");
        check("C owes A $100", Math.abs(cBalances.getOrDefault("A", 0.0) - 100.0) < 0.01);
        check("C owes B $50", Math.abs(cBalances.getOrDefault("B", 0.0) - 50.0) < 0.01);
        System.out.println();
    }

    // --- Test 5: Full settle-up ---
    static void testSettleUp() {
        System.out.println("Test 5: Full Settle-Up");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        svc.addExpense("A", 200, List.of("A", "B"), SplitType.EQUAL, null);
        // B owes A $100

        svc.settleUp("B", "A", 100);
        Map<String, Double> bBalances = svc.getBalances("B");
        check("B fully settled with A", bBalances.getOrDefault("A", 0.0) == 0.0);
        check("No balances remaining for B", bBalances.isEmpty());
        System.out.println();
    }

    // --- Test 6: Partial settle ---
    static void testPartialSettle() {
        System.out.println("Test 6: Partial Settle-Up");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        svc.addExpense("A", 200, List.of("A", "B"), SplitType.EQUAL, null);
        // B owes A $100

        svc.settleUp("B", "A", 40);
        Map<String, Double> bBalances = svc.getBalances("B");
        check("B still owes A $60 after partial settle",
                Math.abs(bBalances.getOrDefault("A", 0.0) - 60.0) < 0.01);
        System.out.println();
    }

    // --- Test 7: Group expense ---
    static void testGroupExpense() {
        System.out.println("Test 7: Group Expense");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        svc.createGroup("G1", "Roommates", List.of("A", "B", "C"));

        Expense exp = svc.addGroupExpense("A", "G1", 300, SplitType.EQUAL, null);
        check("Group expense created", exp.getGroupId().equals("G1"));

        Map<String, Map<String, Double>> groupBalances = svc.getGroupBalances("G1");
        check("Group balances has entries", !groupBalances.isEmpty());

        // B and C each owe A $100
        Map<String, Double> bInGroup = groupBalances.getOrDefault("B", Map.of());
        check("B owes A $100 in group", Math.abs(bInGroup.getOrDefault("A", 0.0) - 100.0) < 0.01);
        System.out.println();
    }

    // --- Test 8: Validation ---
    static void testValidation() {
        System.out.println("Test 8: Validation");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        // Exact split doesn't add up
        try {
            svc.addExpense("A", 100, List.of("A", "B"),
                    SplitType.EXACT, Map.of("A", 60.0, "B", 30.0)); // 90 ≠ 100
            check("Should reject", false);
        } catch (IllegalArgumentException e) {
            check("Exact split validation: amounts must equal total", true);
        }

        // Percent doesn't add to 100
        try {
            svc.addExpense("A", 100, List.of("A", "B"),
                    SplitType.PERCENT, Map.of("A", 60.0, "B", 30.0)); // 90% ≠ 100%
            check("Should reject", false);
        } catch (IllegalArgumentException e) {
            check("Percent split validation: must total 100%", true);
        }

        // Settle more than owed
        svc.addExpense("A", 100, List.of("A", "B"), SplitType.EQUAL, null);
        try {
            svc.settleUp("B", "A", 200); // owes only $50
            check("Should reject", false);
        } catch (IllegalStateException e) {
            check("Cannot settle more than owed", true);
        }

        // Settle when nothing owed
        try {
            svc.settleUp("C", "D", 10); // C doesn't owe D
            check("Should reject", false);
        } catch (IllegalStateException e) {
            check("Cannot settle when nothing owed", true);
        }
        System.out.println();
    }

    // --- Test 9: Concurrent expenses ---
    static void testConcurrentExpenses() throws Exception {
        System.out.println("Test 9: Concurrent Expenses");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        ExecutorService exec = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        // 10 threads: A pays $100 split equally with B
        for (int i = 0; i < 10; i++) {
            futures.add(exec.submit(() ->
                    svc.addExpense("A", 100, List.of("A", "B"), SplitType.EQUAL, null)));
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        // B should owe A $50 × 10 = $500
        Map<String, Double> bBalances = svc.getBalances("B");
        double bOwesA = bBalances.getOrDefault("A", 0.0);
        check("After 10 concurrent expenses: B owes A $500",
                Math.abs(bOwesA - 500.0) < 0.01);
        System.out.println();
    }

    // --- Test 10: Edge cases ---
    static void testEdgeCases() {
        System.out.println("Test 10: Edge Cases");
        System.out.println("-".repeat(56));
        ExpenseService svc = setupService();

        // Payer is only participant → no balance changes
        svc.addExpense("A", 100, List.of("A"), SplitType.EQUAL, null);
        Map<String, Double> aBalances = svc.getBalances("A");
        check("Self-expense: no balances", aBalances.isEmpty());

        // Invalid user
        try {
            svc.addExpense("INVALID", 100, List.of("A", "B"), SplitType.EQUAL, null);
            check("Should reject", false);
        } catch (IllegalArgumentException e) {
            check("Invalid payer rejected", true);
        }

        // Payer not in group
        svc.createGroup("G2", "Test", List.of("A", "B"));
        try {
            svc.addGroupExpense("C", "G2", 100, SplitType.EQUAL, null);
            check("Should reject", false);
        } catch (IllegalArgumentException e) {
            check("Payer not in group rejected", true);
        }

        // Two-way debt nets to zero
        svc.addExpense("A", 100, List.of("A", "B"), SplitType.EQUAL, null); // B owes A $50
        svc.addExpense("B", 100, List.of("A", "B"), SplitType.EQUAL, null); // A owes B $50
        Map<String, Double> netBalances = svc.getBalances("A");
        double netAB = netBalances.getOrDefault("B", 0.0);
        check("Two-way equal debts net to $0", Math.abs(netAB) < 0.01);
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
