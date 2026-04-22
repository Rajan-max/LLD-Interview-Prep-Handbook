# Notification System - LLD Interview Solution 🔔
---

## 1) Requirements (~5 min)

**Prompt**: "Design a notification system that can send messages to users via multiple channels."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What channels? | Email, SMS, Push, In-App |
| **Primary capabilities** | Template support? | Yes — reusable templates with parameter substitution |
| **Primary capabilities** | Broadcast? | Yes — send to all users who opted in |
| **Rules** | User preferences? | Users opt-in/out per channel — respect preferences |
| **Rules** | Priority? | Yes — URGENT > HIGH > MEDIUM > LOW processing order |
| **Rules** | Retry on failure? | Yes — up to 3 retries with exponential backoff |
| **Error handling** | Channel fails? | Retry, then mark FAILED after max retries |
| **Scope** | Concurrent access? | Yes — multiple notifications sent simultaneously |

### Requirements

```
1. Send notifications via multiple channels (Email, SMS, Push, In-App)
2. Template-based notifications with parameter substitution ({name}, {orderId})
3. Direct notifications without templates
4. Broadcast to all users with matching preferences
5. User preference management (opt-in/out per channel)
6. Priority-based processing (URGENT → HIGH → MEDIUM → LOW)
7. Retry with exponential backoff (max 3 attempts)
8. Notification status tracking (PENDING → SENT / FAILED / RETRY)

Out of Scope:
- Real email/SMS gateway integration
- User authentication
- Scheduled notifications
- Rich media (images, videos)
- Analytics and reporting
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- User                  (recipient — owns preferences, immutable)
- Notification          (message — owns status lifecycle)
- NotificationTemplate  (reusable format — immutable, parameter substitution)
- NotificationChannel   (interface — delivery mechanism per channel type)
- NotificationService   (orchestrator — queue, dispatch, retry)

Relationships:
- NotificationService → NotificationChannel (1:N via Map<type, channel>)
- NotificationService → User, Notification, Template repositories
- NotificationTemplate → creates Notification (with param substitution)
- Notification → sent via → NotificationChannel (based on type)
- User → has → Set<NotificationType> preferences
```

**Key decisions:**
- Strategy pattern: NotificationChannel interface with Email/SMS/Push/InApp implementations
- Adding a new channel (WhatsApp, Slack) = one new class + register in service
- Async processing: PriorityBlockingQueue + worker thread pool
- User-level locking: prevents duplicate notifications to same user while allowing parallel processing for different users

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "Multiple channels" | Map<NotificationType, NotificationChannel> | NotificationService |
| "User preferences" | Set<NotificationType> preferences | User |
| "Template with params" | titleTemplate, contentTemplate | NotificationTemplate |
| "Priority processing" | PriorityBlockingQueue<Notification> | NotificationService |
| "Status tracking" | status (PENDING/SENT/FAILED/RETRY), retryCount | Notification |
| "Prevent duplicates" | per-user ReentrantLock | NotificationService |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Send via template | sendNotification(templateId, userId, priority, params) → Notification | NotificationService |
| Send directly | sendDirectNotification(userId, type, title, content, priority) → Notification | NotificationService |
| Broadcast to all | broadcastNotification(type, title, content, priority) → List\<Notification\> | NotificationService |
| Deliver to user | send(user, notification) → boolean | NotificationChannel |
| Create from template | createNotification(userId, priority, params) → Notification | NotificationTemplate |

### Class Outlines

```
class User:                                 // Immutable
  - id, name, email, phone: String
  - preferences: Set<NotificationType>

class Notification:                         // Thread-safe (volatile + synchronized)
  - id: String (auto-generated)
  - userId, title, content: String
  - type: NotificationType
  - priority: Priority
  - status: volatile NotificationStatus (PENDING → SENT / FAILED / RETRY)
  - retryCount: volatile int
  - createdAt, sentAt: LocalDateTime

  + markSent(), markFailed(reason), markRetry()

class NotificationTemplate:                 // Immutable
  - id: String
  - type: NotificationType
  - titleTemplate, contentTemplate: String

  + createNotification(userId, priority, params) → Notification

interface NotificationChannel:              // Strategy pattern
  + send(user, notification) → boolean
  + getType() → NotificationType

class NotificationService:                  // Orchestrator
  - channels: Map<NotificationType, NotificationChannel>
  - userLocks: ConcurrentHashMap<String, ReentrantLock>
  - notificationQueue: PriorityBlockingQueue<Notification>
  - workerPool: ExecutorService
  - retryScheduler: ScheduledExecutorService

  + sendNotification(templateId, userId, priority, params) → Notification
  + sendDirectNotification(userId, type, title, content, priority) → Notification
  + broadcastNotification(type, title, content, priority) → List<Notification>
  + getUserNotifications(userId) → List<Notification>
```

### Key Principle

- **NotificationService** owns the workflow: validate → create → queue → process → retry
- **NotificationChannel** owns delivery: each channel knows how to send (Strategy pattern)
- **Notification** owns its status lifecycle: PENDING → SENT / FAILED / RETRY
- **NotificationTemplate** owns parameter substitution: `{name}` → "John Doe"

---

## 4) Concurrency Control (~5 min)

### Three Questions

**What is shared?**
- Notification.status — workers updating status concurrently
- NotificationQueue — producers adding, consumers taking
- User preferences — concurrent reads during validation

**What can go wrong?**
- Duplicate processing: same notification processed by multiple workers
- Lost status updates: concurrent markSent/markFailed overwrite each other
- Retry storms: multiple retry schedules for same notification

**What's the locking strategy?**
- User-level locking + thread-safe queue. Each user has their own ReentrantLock.

### Why User-Level Locking?

| Approach | Throughput | Decision |
|---|---|---|
| **Global lock** | Very low (serializes everything) | ❌ Too coarse |
| **User-level lock** | High (parallel across users) | ✅ Chosen |
| **Notification-level lock** | Very high but complex | ❌ Over-engineered |

### Concurrency Strategy

```
Shared resources:
- PriorityBlockingQueue — thread-safe by design (producers + consumers)
- Notification.status — protected by synchronized methods on Notification
- Per-user processing — protected by user-level ReentrantLock

Locking approach:
- PriorityBlockingQueue handles producer-consumer safely
- Worker threads call queue.take() (blocking) to get next notification
- Before processing, acquire user-level lock (prevents duplicate delivery)
- Notification.markSent/markFailed/markRetry are synchronized

Thread-safety:
- User: immutable after creation
- Notification: volatile fields + synchronized status methods
- NotificationTemplate: immutable
- NotificationChannel: stateless (no shared state)
- Repositories: ConcurrentHashMap
- Queue: PriorityBlockingQueue (thread-safe)
- Worker pool: ExecutorService
```

**Why PriorityBlockingQueue?**
- Thread-safe producer-consumer pattern built-in
- Priority ordering: URGENT processed before LOW
- Blocking take(): workers sleep when queue is empty (no busy-waiting)

**Why user-level lock (not notification-level)?**
- Prevents duplicate notifications to same user
- Maintains delivery order per user
- Different users processed in parallel (no contention)

---

## 5) Implementation (~10 min)

### Core Method: sendDirectNotification — Queue a Notification

```java
public Notification sendDirectNotification(String userId, NotificationType type,
                                           String title, String content, Priority priority) {
    User user = userRepo.findById(userId);
    if (user == null)
        throw new IllegalArgumentException("User not found: " + userId);
    if (!user.getPreferences().contains(type))
        throw new IllegalStateException("User has disabled " + type + " notifications");

    Notification notification = new Notification(userId, type, title, content, priority);
    notificationRepo.save(notification);
    notificationQueue.offer(notification);  // Non-blocking add to priority queue
    return notification;
}
```

### Core Method: processNotification — Worker Thread Logic

```java
private void processNotification(Notification notification) {
    User user = userRepo.findById(notification.getUserId());
    if (user == null) return;

    // User-level locking — prevents duplicate delivery to same user
    ReentrantLock userLock = userLocks.computeIfAbsent(user.getId(), k -> new ReentrantLock());

    userLock.lock();
    try {
        NotificationChannel channel = channels.get(notification.getType());
        if (channel == null) {
            notification.markFailed("No channel for " + notification.getType());
            return;
        }

        boolean success = channel.send(user, notification);

        if (success) {
            notification.markSent();
        } else if (notification.getRetryCount() < MAX_RETRIES) {
            notification.markRetry();
            scheduleRetry(notification);  // Exponential backoff
        } else {
            notification.markFailed("Max retries exceeded");
        }
    } finally {
        userLock.unlock();
    }
}
```

**What this demonstrates:**
- User-level lock acquired before delivery — no duplicate processing for same user
- Channel lookup via Strategy pattern — doesn't know Email vs SMS
- Retry with exponential backoff on failure
- Final failure after MAX_RETRIES attempts
- Lock released in finally — even if channel.send() throws

### Core Method: sendNotification — Template-Based

```java
public Notification sendNotification(String templateId, String userId,
                                     Priority priority, Map<String, String> params) {
    NotificationTemplate template = templateRepo.findById(templateId);
    if (template == null) throw new IllegalArgumentException("Template not found");

    User user = userRepo.findById(userId);
    if (user == null) throw new IllegalArgumentException("User not found");
    if (!user.getPreferences().contains(template.getType()))
        throw new IllegalStateException("User disabled " + template.getType());

    Notification notification = template.createNotification(userId, priority, params);
    notificationRepo.save(notification);
    notificationQueue.offer(notification);
    return notification;
}
```

### Template Parameter Substitution

```java
// NotificationTemplate.createNotification
public Notification createNotification(String userId, Priority priority, Map<String, String> params) {
    String title = replacePlaceholders(titleTemplate, params);
    String content = replacePlaceholders(contentTemplate, params);
    return new Notification(userId, type, title, content, priority);
}

private String replacePlaceholders(String template, Map<String, String> params) {
    String result = template;
    for (Map.Entry<String, String> entry : params.entrySet()) {
        result = result.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return result;
}
```

### Verification: Walk Through a Scenario

```
Setup: User "U1" with preferences [EMAIL, PUSH]. Template "WELCOME" (EMAIL type).

1. sendNotification("WELCOME", "U1", HIGH, {name: "John"})
   → Template found, user found, EMAIL in preferences ✓
   → Template creates: title="Welcome John!", content="Hello John, welcome!"
   → Notification saved, added to priority queue
   → Returns Notification(PENDING)

2. Worker thread picks up notification (priority queue)
   → Acquires lock for user "U1"
   → Looks up EmailChannel
   → channel.send(user, notification) → true (90% success rate)
   → notification.markSent() → status = SENT
   → Releases lock

3. Another notification for "U2" processed IN PARALLEL (different user lock)

4. If channel.send() returned false:
   → retryCount < 3 → markRetry(), schedule retry in 2^retryCount seconds
   → After 3 failures → markFailed("Max retries exceeded")

✓ User preferences respected. Priority ordering. Retry with backoff.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- Send template notification → verify param substitution in title/content
- Send direct notification → verify status transitions (PENDING → SENT)
- Broadcast to 50 users → only users with matching preferences receive it
- User without EMAIL preference → IllegalStateException on EMAIL notification

**Concurrency tests:**
- **Concurrent sending**: 100 notifications to 10 users (10 each) → all 100 queued, no race conditions
- **Concurrent broadcast**: 5 broadcasts to 50 users → 250 total notifications, no duplicates
- **User-level locking**: 20 threads sending to same user → all 20 processed, no duplicate delivery
- **Priority ordering**: send LOW, HIGH, MEDIUM, URGENT → URGENT processed first

**Edge cases:**
- User not found → IllegalArgumentException
- Template not found → IllegalArgumentException
- Channel send fails → retry up to 3 times, then FAILED
- User disabled channel → IllegalStateException
- Empty broadcast (no users with matching preferences) → empty list

---

## 7) Extensibility (~5 min)

**"How would you add a new channel (WhatsApp, Slack)?"**
> "Implement NotificationChannel interface with the new channel's send logic. Register it in the channels map. No changes to NotificationService or existing channels. This is the Strategy pattern paying off."

```java
class WhatsAppChannel implements NotificationChannel {
    public boolean send(User user, Notification notification) {
        // WhatsApp Business API call
        return whatsAppAPI.sendMessage(user.getPhone(), notification.getContent());
    }
    public NotificationType getType() { return NotificationType.WHATSAPP; }
}
// Register: channels.put(NotificationType.WHATSAPP, new WhatsAppChannel());
```

**"How would you add rate limiting per channel?"**
> "Decorator pattern. Wrap any channel with a RateLimitedChannel that checks a rate limiter before delegating to the real channel. The service doesn't know the difference."

```java
class RateLimitedChannel implements NotificationChannel {
    private final NotificationChannel delegate;
    private final RateLimiter rateLimiter;

    public boolean send(User user, Notification notification) {
        if (!rateLimiter.tryAcquire()) return false;
        return delegate.send(user, notification);
    }
}
```

**"How would you add different retry strategies?"**
> "Extract retry logic into a RetryStrategy interface. Implementations: ExponentialBackoff (2^n seconds), LinearBackoff (n × 30 seconds), NoRetry. Inject into NotificationService."

**"How would you add scheduled notifications (send at a specific time)?"**
> "Add a scheduledAt field to Notification. Use ScheduledExecutorService to delay adding to the queue until the scheduled time. The processing pipeline doesn't change."

**"How would you scale to millions of notifications?"**
> "Replace PriorityBlockingQueue with a distributed message queue (RabbitMQ, Kafka). Partition by user ID for ordering guarantees. Replace in-memory repos with a database. The NotificationChannel interface and processing logic stay the same."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **sendDirectNotification** | O(1) queue insert | O(1) |
| **sendNotification (template)** | O(P) param substitution | O(1) |
| **processNotification** | O(1) + channel latency | O(1) |
| **broadcastNotification** | O(U) | O(U) |
| **getUserNotifications** | O(N) | O(N) |

*P = number of template parameters, U = number of users, N = user's notification count.*

---

**Implementation**: See [NotificationSystemComplete.java](./NotificationSystemComplete.java)
