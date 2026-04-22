# Splitwise System - LLD Interview Solution 💰
---

## 1) Requirements (~5 min)

**Prompt**: "Design an expense-sharing system like Splitwise."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | Add expense, split among users, view balances, settle up |
| **Primary capabilities** | Split types? | Equal, exact amounts, percentage-based |
| **Rules** | How are balances tracked? | Net balance between every pair of users (who owes whom) |
| **Rules** | Groups? | Yes — users can create groups and add expenses to groups |
| **Rules** | Simplify debts? | Out of scope for now (discuss in extensibility) |
| **Error handling** | Invalid split (amounts don't add up)? | Reject with error |
| **Error handling** | Settle more than owed? | Reject with error |
| **Scope** | Concurrent access? | Yes — multiple users adding expenses simultaneously |

### Requirements

```
1. Users can register in the system
2. Users can create groups and add members
3. Add expense with different split strategies:
   - EQUAL: split equally among participants
   - EXACT: specify exact amount each person owes
   - PERCENT: specify percentage each person owes
4. Track net balances between every pair of users (who owes whom, how much)
5. Users can settle up (pay back) partially or fully
6. View balances: per-user (all debts) and per-group
7. Prevent inconsistent balance updates under concurrent access

Out of Scope:
- Payment gateway integration
- Currency conversion
- Expense categories and tags
- Debt simplification (minimize transactions)
- Expense history / undo
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- User              (registered user — id, name, email)
- Group             (collection of users — id, name, members)
- Expense           (a bill — paidBy, amount, splits, group)
- Split             (one user's share — userId, amount)
- SplitStrategy     (interface — how to divide an expense: EQUAL, EXACT, PERCENT)
- BalanceSheet      (tracks net balances between all user pairs)
- ExpenseService    (orchestrator — add expense, settle, view balances)

NOT entities:
- Transaction       (settle-up is just a negative expense — no separate class needed)

Relationships:
- Group → User (N:M, group has members)
- Expense → User (paidBy), List<Split> (who owes what)
- Expense → Group (optional, can be groupless)
- ExpenseService → BalanceSheet (updates on every expense/settlement)
- ExpenseService → SplitStrategy (calculates splits via Strategy pattern)
```

**Key decisions:**
- BalanceSheet uses a `Map<String, Map<String, Double>>` — balanceSheet[A][B] = amount A owes B
- Net balance: if A owes B $50 and B owes A $20, net is A owes B $30
- SplitStrategy is the Strategy pattern — EQUAL, EXACT, PERCENT are implementations
- Settle-up is modeled as a direct balance adjustment, not a new expense
- Balance updates are synchronized per user-pair to prevent lost updates

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Users register" | id, name, email | User |
| "Create groups with members" | id, name, Set<userId> | Group |
| "Add expense with splits" | id, paidBy, amount, splits, groupId | Expense |
| "Each user's share" | userId, amount | Split |
| "Net balances between pairs" | Map<userId, Map<userId, Double>> | BalanceSheet |
| "Different split strategies" | calculateSplits(amount, users, params) | SplitStrategy |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Add expense with split | addExpense(paidBy, amount, participants, splitType, params) → Expense | ExpenseService |
| Add group expense | addGroupExpense(paidBy, groupId, amount, splitType, params) → Expense | ExpenseService |
| Settle up between users | settleUp(fromUser, toUser, amount) | ExpenseService |
| View user's balances | getBalances(userId) → Map<userId, Double> | ExpenseService |
| View group balances | getGroupBalances(groupId) → Map<userId, Map<userId, Double>> | ExpenseService |
| Calculate splits | calculateSplits(amount, participants, params) → List\<Split\> | SplitStrategy |

### Class Outlines

```
class User:
  - id, name, email: String

class Group:
  - id, name: String
  - memberIds: Set<String>

  + addMember(userId), removeMember(userId)

class Split:
  - userId: String
  - amount: double

class Expense:
  - id: String (auto-generated)
  - paidBy: String (userId)
  - amount: double
  - splits: List<Split>
  - groupId: String (nullable)

interface SplitStrategy:
  + calculateSplits(amount, participantIds, params) → List<Split>
  + validate(amount, participantIds, params) → boolean

class EqualSplitStrategy implements SplitStrategy
class ExactSplitStrategy implements SplitStrategy
class PercentSplitStrategy implements SplitStrategy

class BalanceSheet:
  - balances: ConcurrentHashMap<String, ConcurrentHashMap<String, Double>>

  + updateBalance(fromUser, toUser, amount)     // fromUser owes toUser
  + getBalance(userA, userB) → double           // positive = A owes B
  + getUserBalances(userId) → Map<String, Double>

class ExpenseService:                           // Orchestrator
  - users, groups: Maps
  - expenses: List<Expense>
  - balanceSheet: BalanceSheet
  - strategies: Map<SplitType, SplitStrategy>

  + addExpense(paidBy, amount, participantIds, splitType, params) → Expense
  + addGroupExpense(paidBy, groupId, amount, splitType, params) → Expense
  + settleUp(fromUser, toUser, amount)
  + getBalances(userId) → Map<String, Double>
  + getGroupBalances(groupId) → Map<String, Map<String, Double>>
```

### Key Principle

- **BalanceSheet** owns all balance state — single source of truth for who owes whom
- **SplitStrategy** owns split calculation — validates and divides the amount
- **ExpenseService** owns the workflow — validate → split → update balances
- **Expense** is a record — immutable after creation, stored for history

### Balance Update Logic

When user A pays $300 split equally among A, B, C:
- B owes A: $100 (B's share, paid by A)
- C owes A: $100 (C's share, paid by A)
- A's own share ($100) is not tracked (you don't owe yourself)

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- BalanceSheet — multiple expenses updating balances between the same user pairs concurrently

**What can go wrong?**
- Lost update: Thread A reads balance(X,Y)=50, Thread B reads balance(X,Y)=50, both add $10, result is 60 instead of 70
- Inconsistent state: partial balance update (some splits applied, others not)

**What's the locking strategy?**
- Balance-pair locking. Each (userA, userB) pair has its own synchronization via the BalanceSheet.

### Why Balance-Pair Locking?

| Approach | Throughput | Decision |
|---|---|---|
| **Global lock on BalanceSheet** | Very low (serializes all updates) | ❌ Too coarse |
| **User-level lock** | Medium (blocks all expenses involving a user) | ❌ Still coarse |
| **Balance-pair lock** | High (only same-pair updates contend) | ✅ Chosen |

### Concurrency Strategy

```
Shared resource:
- balances[userA][userB] — multiple expenses updating same pair

Race condition prevented:
- Lost update: synchronized updateBalance ensures atomic read-modify-write

Locking approach:
- BalanceSheet.updateBalance is synchronized on the inner map for the user
- ConcurrentHashMap for outer map (user → their balances)
- Each expense updates multiple pairs — but each pair update is independent and atomic

Thread-safety:
- User, Group: immutable after creation
- Expense, Split: immutable after creation
- BalanceSheet: ConcurrentHashMap + synchronized updateBalance
- ExpenseService: thread-safe via BalanceSheet's internal synchronization
```

**No deadlock risk:**
Each updateBalance call locks only one user's inner map. An expense with 3 participants updates 2 pairs sequentially (not simultaneously), so no circular wait.

---

## 5) Implementation (~10 min)

### Core Method: addExpense

```java
public Expense addExpense(String paidBy, double amount, List<String> participantIds,
                          SplitType splitType, Map<String, Double> params) {
    if (!users.containsKey(paidBy))
        throw new IllegalArgumentException("Payer not found");
    for (String id : participantIds)
        if (!users.containsKey(id))
            throw new IllegalArgumentException("Participant not found: " + id);

    SplitStrategy strategy = strategies.get(splitType);
    if (!strategy.validate(amount, participantIds, params))
        throw new IllegalArgumentException("Invalid split parameters");

    List<Split> splits = strategy.calculateSplits(amount, participantIds, params);
    Expense expense = new Expense(paidBy, amount, splits, null);
    expenses.add(expense);

    // Update balances: each participant (except payer) owes the payer
    for (Split split : splits) {
        if (!split.getUserId().equals(paidBy)) {
            balanceSheet.updateBalance(split.getUserId(), paidBy, split.getAmount());
        }
    }
    return expense;
}
```

### Core Method: BalanceSheet.updateBalance — Net Balance Tracking

```java
public void updateBalance(String fromUser, String toUser, double amount) {
    // fromUser owes toUser `amount` more
    // Net it: if toUser already owes fromUser, reduce that first
    ConcurrentHashMap<String, Double> fromBalances =
            balances.computeIfAbsent(fromUser, k -> new ConcurrentHashMap<>());

    synchronized (fromBalances) {
        double currentFromOwes = fromBalances.getOrDefault(toUser, 0.0);
        double currentToOwes = balances
                .getOrDefault(toUser, new ConcurrentHashMap<>())
                .getOrDefault(fromUser, 0.0);

        if (currentToOwes > 0) {
            // toUser owes fromUser — net it out
            double net = amount - currentToOwes;
            if (net > 0) {
                // fromUser still owes after netting
                fromBalances.put(toUser, currentFromOwes + net);
                balances.computeIfAbsent(toUser, k -> new ConcurrentHashMap<>())
                        .remove(fromUser);
            } else if (net < 0) {
                // toUser still owes after netting
                fromBalances.remove(toUser);
                balances.computeIfAbsent(toUser, k -> new ConcurrentHashMap<>())
                        .put(fromUser, -net);
            } else {
                // Perfectly settled
                fromBalances.remove(toUser);
                balances.computeIfAbsent(toUser, k -> new ConcurrentHashMap<>())
                        .remove(fromUser);
            }
        } else {
            fromBalances.put(toUser, currentFromOwes + amount);
        }
    }
}
```

**What this demonstrates:**
- Net balancing: if A owes B $50 and B owes A $30, net is A owes B $20
- Synchronized on the inner map to prevent lost updates
- Removes zero-balance entries to keep the map clean

### Split Strategies

```java
// EQUAL: divide evenly, round to 2 decimal places
class EqualSplitStrategy implements SplitStrategy {
    public List<Split> calculateSplits(double amount, List<String> participants,
                                       Map<String, Double> params) {
        double share = Math.round(amount / participants.size() * 100.0) / 100.0;
        return participants.stream()
                .map(id -> new Split(id, share))
                .toList();
    }
}

// EXACT: params = {userId → exact amount}
class ExactSplitStrategy implements SplitStrategy {
    public boolean validate(double amount, List<String> participants,
                            Map<String, Double> params) {
        double total = params.values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.abs(total - amount) < 0.01; // must add up to total
    }
}

// PERCENT: params = {userId → percentage}
class PercentSplitStrategy implements SplitStrategy {
    public boolean validate(double amount, List<String> participants,
                            Map<String, Double> params) {
        double totalPercent = params.values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.abs(totalPercent - 100.0) < 0.01; // must add up to 100%
    }

    public List<Split> calculateSplits(double amount, List<String> participants,
                                       Map<String, Double> params) {
        return participants.stream()
                .map(id -> new Split(id, Math.round(amount * params.get(id) / 100.0 * 100.0) / 100.0))
                .toList();
    }
}
```

### Core Method: settleUp

```java
public void settleUp(String fromUser, String toUser, double amount) {
    if (!users.containsKey(fromUser) || !users.containsKey(toUser))
        throw new IllegalArgumentException("User not found");
    if (amount <= 0)
        throw new IllegalArgumentException("Amount must be positive");

    double owed = balanceSheet.getBalance(fromUser, toUser);
    if (owed <= 0)
        throw new IllegalStateException(fromUser + " doesn't owe " + toUser);
    if (amount > owed + 0.01)
        throw new IllegalStateException("Cannot settle more than owed: $" + owed);

    // Reverse the debt: toUser now "owes" fromUser (reduces fromUser's debt)
    balanceSheet.updateBalance(toUser, fromUser, amount);
}
```

### Verification: Walk Through a Scenario

```
Setup: Users A, B, C

Expense 1: A pays $300, split EQUAL among A, B, C
  → Each share = $100
  → B owes A: $100
  → C owes A: $100
  → Balances: {B→A: $100, C→A: $100}

Expense 2: B pays $150, split EQUAL among A, B, C
  → Each share = $50
  → A owes B: $50
  → C owes B: $50
  → But B already owes A $100 → net: B owes A $50 (100 - 50 = 50)
  → Balances: {B→A: $50, C→A: $100, C→B: $50}

Settle: B pays A $50
  → B→A balance was $50 → now $0 (removed)
  → Balances: {C→A: $100, C→B: $50}

✓ Net balancing works. Settle-up reduces debt correctly.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Equal split: $300 among 3 → each owes $100
- Exact split: $100 as {A:60, B:40} → validates sum = total
- Percent split: $200 as {A:50%, B:30%, C:20%} → $100, $60, $40
- Settle-up: partial ($30 of $100) and full ($100 of $100)
- Group expense: add to group, verify only group members participate

**Validation tests:**
- Exact split amounts don't add up → IllegalArgumentException
- Percent split doesn't total 100% → IllegalArgumentException
- Settle more than owed → IllegalStateException
- Settle when nothing owed → IllegalStateException

**Concurrency tests:**
- **Parallel expenses**: 10 threads adding expenses between same users → balances consistent
- **Settle + expense race**: settle-up while new expense added → no lost updates

**Edge cases:**
- Payer is also a participant (their share is not tracked as debt)
- Two-way debts net out correctly (A owes B $50, B owes A $30 → A owes B $20)
- Zero balance entries cleaned up from map
- Single-person expense (payer = only participant) → no balance changes

---

## 7) Extensibility (~5 min)

**"How would you add debt simplification (minimize transactions)?"**
> "Calculate net balance per user across all pairs. Users with positive net are creditors, negative are debtors. Greedily match largest debtor with largest creditor. This is a separate SimplificationService that reads from BalanceSheet — doesn't change the core expense logic."

**"How would you add a new split type (e.g., by shares/weights)?"**
> "Implement SplitStrategy interface. ShareBasedSplitStrategy takes weights like {A:2, B:1, C:1} and divides proportionally. Register in the strategies map. No changes to ExpenseService."

```java
class ShareBasedSplitStrategy implements SplitStrategy {
    public List<Split> calculateSplits(double amount, List<String> participants,
                                       Map<String, Double> params) {
        double totalShares = params.values().stream().mapToDouble(Double::doubleValue).sum();
        return participants.stream()
                .map(id -> new Split(id, amount * params.get(id) / totalShares))
                .toList();
    }
}
```

**"How would you add expense categories and monthly reports?"**
> "Add a category field to Expense. Create a ReportService that queries expenses by category/date range and aggregates. The core splitting and balance logic doesn't change."

**"How would you handle currency conversion?"**
> "Add a currency field to Expense. Inject a CurrencyConverter that converts to a base currency before updating BalanceSheet. Balances are always stored in base currency."

**"How would you scale to millions of users?"**
> "Partition BalanceSheet by user ID range. Each partition handles a subset of user pairs. For cross-partition expenses, use a distributed transaction or eventual consistency. Replace ConcurrentHashMap with Redis or a database."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **addExpense** | O(P) splits + O(P) balance updates | O(P) |
| **settleUp** | O(1) | O(1) |
| **getBalances(userId)** | O(B) | O(B) |
| **getGroupBalances** | O(M × B) | O(M × B) |

*P = participants in expense, B = number of users with balance against this user, M = group members.*

---

**Implementation**: See [SplitwiseSystemComplete.java](./SplitwiseSystemComplete.java)
