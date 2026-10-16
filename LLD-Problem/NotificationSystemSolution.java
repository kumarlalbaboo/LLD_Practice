import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/* 
 * Functional Requirements:
----------------------------
    1. Send Email/SMS/Push Notification to users based on their preferences. - Factory Design Pattern.
    2. Async submission of notification to the notification handler/system. - Observer Design Pattern.
    3. Rate Limiting/Formatting/Retry mechanism for failed notifications. - Decorator Design Pattern.
    4. Easy to add new notification channels in the future. - Strategy Design Pattern.
    5. Track the status of notifications (Sent, Failed, Pending).

Non-Functional Requirements:
----------------------------
    1. Thread-safety
    2. Async submission of notifications
    3. Scalability/Extensibility
    4. SOLID principles should be followed

    Entities:
    ----------------------------
        Notification
        NotificationService
        NotificationChannel
            SMSNotificationChannel
            EmailNotificationChannel
            PushNotificationChannel
        UserPreferenceService
        NotificationDispatcherService

     * Patterns:
 * 1. Factory  - NotificationChannelFactory creates/resolves channels.
 * 2. Observer - NotificationObserver receives per-channel status changes.
 * 3. Decorator - Formatting, retry, and rate limiting wrap channel behavior.
 * 4. Strategy - NotificationChannel implementations are interchangeable.
*/

enum NotificationType {
    SMS, EMAIL, PUSH
}

enum NotificationStatus {
    PENDING, SENT, FAILED
}

/** Immutable notification data. */
final class Notification {
    private final String id;
    private final String recipientId;
    private final String userId;
    private final String message;
    private final long createdAt;

    public Notification(String id, String recipientId, String userId, String message) {
        this(id, recipientId, userId, message, System.currentTimeMillis());
    }

    private Notification(
            String id, String recipientId, String userId, String message, long createdAt) {
        this.id = requireText(id, "id");
        this.recipientId = requireText(recipientId, "recipientId");
        this.userId = requireText(userId, "userId");
        this.message = Objects.requireNonNull(message, "message");
        this.createdAt = createdAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public String getUserId() { return userId; }
    public String getMessage() { return message; }
    public long getCreatedAt() { return createdAt; }

    public Notification withMessage(String newMessage) {
        return new Notification(id, recipientId, userId, newMessage, createdAt);
    }

    @Override
    public String toString() {
        return "Notification{id='" + id + "', userId='" + userId
                + "', recipientId='" + recipientId + "', message='" + message + "'}";
    }
}

/** Strategy interface: every channel can be substituted for another. */
interface NotificationChannel {
    void sendNotification(Notification notification);
}

final class SMSNotificationChannel implements NotificationChannel {
    @Override
    public void sendNotification(Notification notification) {
        System.out.println("[SMS] Sending to " + notification.getRecipientId()
                + ": " + notification.getMessage());
    }
}

final class EmailNotificationChannel implements NotificationChannel {
    @Override
    public void sendNotification(Notification notification) {
        System.out.println("[EMAIL] Sending to " + notification.getRecipientId()
                + ": " + notification.getMessage());
    }
}

final class PushNotificationChannel implements NotificationChannel {
    @Override
    public void sendNotification(Notification notification) {
        System.out.println("[PUSH] Sending to " + notification.getRecipientId()
                + ": " + notification.getMessage());
    }
}

/** Base Decorator. */
abstract class NotificationDecorator implements NotificationChannel {
    protected final NotificationChannel wrappedChannel;

    protected NotificationDecorator(NotificationChannel wrappedChannel) {
        this.wrappedChannel = Objects.requireNonNull(wrappedChannel, "wrappedChannel");
    }

    @Override
    public void sendNotification(Notification notification) {
        wrappedChannel.sendNotification(notification);
    }
}

final class FormattingDecorator extends NotificationDecorator {
    public FormattingDecorator(NotificationChannel wrappedChannel) {
        super(wrappedChannel);
    }

    @Override
    public void sendNotification(Notification notification) {
        String formatted = notification.getMessage().trim();
        wrappedChannel.sendNotification(notification.withMessage(formatted));
    }
}

/** Retries RuntimeExceptions thrown by the underlying delivery channel. */
final class RetryDecorator extends NotificationDecorator {
    private final int maxRetries;
    private final long retryDelayMs;

    public RetryDecorator(NotificationChannel wrappedChannel, int maxRetries, long retryDelayMs) {
        super(wrappedChannel);
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("retryDelayMs must be >= 0");
        }
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public void sendNotification(Notification notification) {
        int retries = 0;
        while (true) {
            try {
                wrappedChannel.sendNotification(notification);
                return;
            } catch (RuntimeException ex) {
                if (retries >= maxRetries) {
                    throw new NotificationDeliveryException(
                            "Delivery failed for notification " + notification.getId()
                                    + " after " + (retries + 1) + " attempt(s)", ex);
                }
                retries++;
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new NotificationDeliveryException(
                            "Retry interrupted for notification " + notification.getId(),
                            interrupted);
                }
            }
        }
    }
}

/**
 * Serializes sends through this decorator, ensuring a minimum interval between
 * completed/start attempts for this channel instance. This is a simple in-process
 * limiter, not a distributed rate limiter.
 */
final class RateLimitDecorator extends NotificationDecorator {
    private final long minIntervalMs;
    private long lastAttemptAt;

    public RateLimitDecorator(NotificationChannel wrappedChannel, long minIntervalMs) {
        super(wrappedChannel);
        if (minIntervalMs < 0) {
            throw new IllegalArgumentException("minIntervalMs must be >= 0");
        }
        this.minIntervalMs = minIntervalMs;
    }

    @Override
    public synchronized void sendNotification(Notification notification) {
        long waitMs = minIntervalMs - (System.currentTimeMillis() - lastAttemptAt);
        if (lastAttemptAt > 0 && waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new NotificationDeliveryException(
                        "Rate-limit wait interrupted for " + notification.getId(), interrupted);
            }
        }

        lastAttemptAt = System.currentTimeMillis();
        wrappedChannel.sendNotification(notification);
    }
}

final class NotificationDeliveryException extends RuntimeException {
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

/** Observer event is scoped to a notification and a delivery channel. */
interface NotificationObserver {
    void onStatusChanged(
            Notification notification, NotificationType type, NotificationStatus status);
}

final class NotificationStatusTracker implements NotificationObserver {
    private final Map<String, Map<NotificationType, NotificationStatus>> statuses =
            new ConcurrentHashMap<String, Map<NotificationType, NotificationStatus>>();

    @Override
    public void onStatusChanged(
            Notification notification, NotificationType type, NotificationStatus status) {
        statuses.computeIfAbsent(notification.getId(),
                id -> new ConcurrentHashMap<NotificationType, NotificationStatus>())
                .put(type, status);
    }

    public NotificationStatus getStatus(String notificationId, NotificationType type) {
        Map<NotificationType, NotificationStatus> perChannel = statuses.get(notificationId);
        return perChannel == null ? null : perChannel.get(type);
    }

    public Map<NotificationType, NotificationStatus> getStatuses(String notificationId) {
        Map<NotificationType, NotificationStatus> perChannel = statuses.get(notificationId);
        if (perChannel == null) {
            return Collections.emptyMap();
        }
        Map<NotificationType, NotificationStatus> snapshot =
                new EnumMap<NotificationType, NotificationStatus>(NotificationType.class);
        snapshot.putAll(perChannel);
        return Collections.unmodifiableMap(snapshot);
    }

    public Map<String, Map<NotificationType, NotificationStatus>> getAllStatuses() {
        Map<String, Map<NotificationType, NotificationStatus>> snapshot =
                new HashMap<String, Map<NotificationType, NotificationStatus>>();
        for (String id : statuses.keySet()) {
            snapshot.put(id, getStatuses(id));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}

/** Stores listeners and prevents one broken observer from blocking the others. */
final class NotificationEventPublisher {
    private final List<NotificationObserver> observers =
            Collections.synchronizedList(new ArrayList<NotificationObserver>());

    public void subscribe(NotificationObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void publish(
            Notification notification, NotificationType type, NotificationStatus status) {
        List<NotificationObserver> snapshot;
        synchronized (observers) {
            snapshot = new ArrayList<NotificationObserver>(observers);
        }
        for (NotificationObserver observer : snapshot) {
            try {
                observer.onStatusChanged(notification, type, status);
            } catch (RuntimeException ex) {
                System.err.println("[OBSERVER ERROR] " + ex.getMessage());
            }
        }
    }
}

/** Factory with an injectable registry, rather than a static hard-coded registry. */
final class NotificationChannelFactory {
    private final Map<NotificationType, NotificationChannel> channels =
            new EnumMap<NotificationType, NotificationChannel>(NotificationType.class);

    public NotificationChannelFactory(Map<NotificationType, NotificationChannel> channels) {
        if (channels != null) {
            this.channels.putAll(channels);
        }
    }

    public NotificationChannel getChannel(NotificationType type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("No channel registered for: " + type);
        }
        return channel;
    }

    public static NotificationChannelFactory createDefault() {
        Map<NotificationType, NotificationChannel> registry =
                new EnumMap<NotificationType, NotificationChannel>(NotificationType.class);

        registry.put(NotificationType.SMS,
                new RateLimitDecorator(
                        new RetryDecorator(new SMSNotificationChannel(), 2, 200), 150));
        registry.put(NotificationType.EMAIL,
                new RateLimitDecorator(
                        new RetryDecorator(
                                new FormattingDecorator(new EmailNotificationChannel()), 2, 200),
                        150));
        registry.put(NotificationType.PUSH,
                new RateLimitDecorator(
                        new RetryDecorator(
                                new FormattingDecorator(new PushNotificationChannel()), 2, 200),
                        150));
        return new NotificationChannelFactory(registry);
    }
}

/** Thread-safe user preferences; never exposes its mutable internal sets. */
final class UserPreferenceService {
    private final Map<String, Set<NotificationType>> preferences =
            new ConcurrentHashMap<String, Set<NotificationType>>();

    public void updatePreferences(String userId, Set<NotificationType> channels) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(channels, "channels");

        EnumSet<NotificationType> copy = channels.isEmpty()
                ? EnumSet.noneOf(NotificationType.class)
                : EnumSet.copyOf(channels);
        preferences.put(userId, Collections.unmodifiableSet(copy));
    }

    public Set<NotificationType> getPreferences(String userId) {
        Set<NotificationType> channels = preferences.get(userId);
        if (channels == null) {
            return Collections.unmodifiableSet(EnumSet.noneOf(NotificationType.class));
        }
        return channels;
    }
}

final class NotificationDispatcherService implements AutoCloseable {
    private final ExecutorService executor;
    private final NotificationChannelFactory factory;
    private final NotificationEventPublisher publisher;

    public NotificationDispatcherService(
            NotificationChannelFactory factory,
            NotificationEventPublisher publisher,
            int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be > 0");
        }
        this.factory = Objects.requireNonNull(factory, "factory");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.executor = Executors.newFixedThreadPool(poolSize);
    }

    public void submit(Notification notification, Set<NotificationType> preferredChannels) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(preferredChannels, "preferredChannels");

        if (preferredChannels.isEmpty()) {
            return;
        }

        // Snapshot caller-owned state before crossing the asynchronous boundary.
        final Set<NotificationType> channelSnapshot =
                Collections.unmodifiableSet(EnumSet.copyOf(preferredChannels));

        // Publish PENDING before scheduling, so fast tasks cannot race ahead of it.
        for (NotificationType type : channelSnapshot) {
            publisher.publish(notification, type, NotificationStatus.PENDING);
        }

        try {
            executor.execute(() -> {
                for (NotificationType type : channelSnapshot) {
                    try {
                        factory.getChannel(type).sendNotification(notification);
                        publisher.publish(notification, type, NotificationStatus.SENT);
                    } catch (Exception ex) {
                        publisher.publish(notification, type, NotificationStatus.FAILED);
                        System.err.println("[ERROR] Notification " + notification.getId()
                                + " via " + type + " failed: " + ex.getMessage());
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            for (NotificationType type : channelSnapshot) {
                publisher.publish(notification, type, NotificationStatus.FAILED);
            }
            throw ex;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

final class NotificationService {
    private final UserPreferenceService preferenceService;
    private final NotificationDispatcherService dispatcher;

    public NotificationService(
            UserPreferenceService preferenceService,
            NotificationDispatcherService dispatcher) {
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public void sendNotification(Notification notification) {
        Set<NotificationType> channels =
                preferenceService.getPreferences(notification.getUserId());
        if (channels.isEmpty()) {
            System.out.println("[INFO] No notification preferences for user "
                    + notification.getUserId());
            return;
        }
        dispatcher.submit(notification, channels);
    }
}

public class NotificationSystemSolution {
    public static void main(String[] args) throws InterruptedException {
        System.out.println();
        System.out.println("Notification System LLD Demonstration\n");
        UserPreferenceService preferences = new UserPreferenceService();
        preferences.updatePreferences("user-1",
                EnumSet.of(NotificationType.EMAIL, NotificationType.SMS));
        preferences.updatePreferences("user-2", EnumSet.of(NotificationType.PUSH));

        NotificationStatusTracker tracker = new NotificationStatusTracker();
        NotificationEventPublisher publisher = new NotificationEventPublisher();
        publisher.subscribe(tracker);

        NotificationChannelFactory factory = NotificationChannelFactory.createDefault();

        try (NotificationDispatcherService dispatcher =
                     new NotificationDispatcherService(factory, publisher, 4)) {
            NotificationService service = new NotificationService(preferences, dispatcher);

            service.sendNotification(new Notification(
                    "n-1001", "user1@example.com", "user-1", " Your order has shipped. "));
            service.sendNotification(new Notification(
                    "n-1002", "user-2-push-token", "user-2",
                    "Your meeting starts in 10 minutes."));

            dispatcher.shutdown();
            dispatcher.awaitTermination(10, TimeUnit.SECONDS);

            System.out.println("\nPer-channel notification statuses:");
            System.out.println(tracker.getAllStatuses());
        }

        System.out.println("\nRetry demonstration (2 retries allowed):");
        AtomicLong attemptCount = new AtomicLong();
        NotificationChannel temporarilyFailingChannel = notification -> {
            long attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                System.out.println(" Attempt " + attempt);
                throw new RuntimeException("Temporary provider error");
            }
            System.out.println(" Attempt " + attempt + " delivered notification "
                    + notification.getId());
        };

        NotificationChannel retryingChannel = new RetryDecorator(temporarilyFailingChannel, 2, 200);
        retryingChannel.sendNotification(new Notification(
                "n-retry-demo", "retry-demo-recipient", "retry-demo-user",
                "This message succeeds after two retries."));
    }
}
