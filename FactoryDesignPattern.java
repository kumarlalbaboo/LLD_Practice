/**
 * Factory Design Pattern
 * 
 * The Factory Design Pattern is a creational design pattern that provides an interface for creating objects in a superclass, but allows subclasses to alter the type of objects 
 * that will be created. It promotes loose coupling by eliminating the need to bind application-specific classes into the code.
 * 
 * In this example, we have a Notification interface and three concrete implementations: SMSNotification, EmailNotification, and WhatsAppNotification. The NotificationFactory class
 *  is responsible for creating instances of these notification types based on the provided input.
 * 
 * The OrderService class uses the factory to create the appropriate notification type and send a message when an order is placed.
 */


// PRODUCT
interface Notification {

    void send(String message);
}

// CONCRETE PRODUCTS
class SMSNotification implements Notification {

    @Override
    public void send(String message) {
        System.out.println("Sending SMS: " + message);
    }
}

class EmailNotification implements Notification {

    @Override
    public void send(String message) {
        System.out.println("Sending Email: " + message);
    }
}

class WhatsAppNotification implements Notification {

    @Override
    public void send(String message) {
        System.out.println("Sending WhatsApp: " + message);
    }
}

// FACTORY
interface NotificationFactory {

    Notification getNotification();
}

// CONCRETE FACTORIES
class SMSNotificationFactory implements NotificationFactory {

    @Override
    public Notification getNotification() {
        return new SMSNotification();
    }
}

class EmailNotificationFactory implements NotificationFactory {

    @Override
    public Notification getNotification() {
        return new EmailNotification();
    }
}

class WhatsAppNotificationFactory implements NotificationFactory {

    @Override
    public Notification getNotification() {
        return new WhatsAppNotification();
    }
}

// BUSINESS SERVICE / CLIENT
class OrderService {

    private NotificationFactory notificationFactory;

    public OrderService(NotificationFactory notificationFactory) {
        this.notificationFactory = notificationFactory;
    }

    public void placeOrder() {

        System.out.println("Order placed successfully.");

        // Ask factory to create the required notification
        Notification notification = notificationFactory.getNotification();

        // Use the notification
        notification.send("Your order has been placed successfully.");
    }
}

// MAIN
public class FactoryDesignPattern {

    public static void main(String[] args) {

        // SMS
        NotificationFactory smsFactory = new SMSNotificationFactory();
        OrderService smsOrderService = new OrderService(smsFactory);
        smsOrderService.placeOrder();


        System.out.println();

        // Email
        NotificationFactory emailFactory = new EmailNotificationFactory();
        OrderService emailOrderService = new OrderService(emailFactory);
        emailOrderService.placeOrder();

        System.out.println();

        // WhatsApp
        NotificationFactory whatsappFactory = new WhatsAppNotificationFactory();
        OrderService whatsappOrderService = new OrderService(whatsappFactory);
        whatsappOrderService.placeOrder();

        System.out.println();
    }
}





/**
 * Factory Design Pattern
 * 
 * The Factory Design Pattern is a creational design pattern that provides an interface for creating objects in a superclass, but allows subclasses to alter the type of objects that will be created. It promotes loose coupling by eliminating the need to bind application-specific classes into the code.
 * 
 * In this example, we have a Notification interface and three concrete implementations: SMSNotification, EmailNotification, and WhatsAppNotification. The NotificationFactory class is responsible for creating instances of these notification types based on the provided input.
 * 
 * The OrderService class uses the factory to create the appropriate notification type and send a message when an order is placed.
 */
/*
interface Notification {

    void send(String message);
}

class SMSNotification implements Notification {

    @Override
    public void send(String message) {
        System.out.println("Sending SMS: " + message);
    }
}

class EmailNotification implements Notification {

    @Override
    public void send(String message) {
        System.out.println("Sending Email: " + message);
    }
}

class WhatsAppNotification implements Notification {

    @Override
    public void send(String message) {
        System.out.println("Sending WhatsApp: " + message);
    }
}

// Simple Factory Design Pattern
class NotificationFactory {

    public static Notification createNotification(String type) {

        if ("SMS".equalsIgnoreCase(type)) {
            return new SMSNotification();
        }

        if ("EMAIL".equalsIgnoreCase(type)) {
            return new EmailNotification();
        }

        if ("WHATSAPP".equalsIgnoreCase(type)) {
            return new WhatsAppNotification();
        }

        throw new IllegalArgumentException(
                "Invalid notification type: " + type
        );
    }
}

class OrderService {

    public void placeOrder(String notificationType) {

        System.out.println("Order placed successfully.");

        Notification notification =
                NotificationFactory.createNotification(notificationType);

        notification.send("Your order has been placed successfully.");
    }
}

public class FactoryDesignPattern {

    public static void main(String[] args) {

        OrderService orderService = new OrderService();

        orderService.placeOrder("SMS");

        orderService.placeOrder("EMAIL");

        orderService.placeOrder("WHATSAPP");
    }
}
*/



