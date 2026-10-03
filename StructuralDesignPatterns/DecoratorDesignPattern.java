//package StructuralDesignPatterns;

// Component
interface Notification {
    void send(String message);
}

// Concrete Component
class SMSNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("Sending SMS notification: " + message);
    }
}

// Base Decorator
abstract class NotificationDecorator implements Notification {

    protected final Notification wrapped;

    public NotificationDecorator(Notification wrapped) {
        this.wrapped = wrapped;
    }
}

// Concrete Decorator 1
class RetryDecorator extends NotificationDecorator {

    public RetryDecorator(Notification wrapped) {
        super(wrapped);
    }

    @Override
    public void send(String message) {

        System.out.println("Retrying logic applied...");
        wrapped.send(message);
    }
}

// Concrete Decorator 2
class LoadBalancerDecorator extends NotificationDecorator {
    
    public LoadBalancerDecorator(Notification wrapped) {
        super(wrapped);
    }

    @Override
    public void send(String message) {
        System.out.println("Load balancing the notification...");

        wrapped.send(message);
    }

}

// Client code
public class DecoratorDesignPattern {

    public static void main(String[] args) {
        
        Notification notification = new SMSNotification();

        // Wrap the SMS notification with a retry decorator
        notification = new RetryDecorator(notification);

        // Wrap the notification with a load balancer decorator
        notification = new LoadBalancerDecorator(notification);

        // Send the notification
        notification.send("Hello, this is a test notification!");
        
    }
    
}
