
// Abstract Products
interface PaymentProcessor {
    void pay(double amount);
}

interface RefundProcessor {
    void refund(String transactionId);
}

interface PaymentValidator {
    boolean validate(double amount);
}

//Abstract Factory
interface PaymentFactory {
    PaymentProcessor createPaymentProcessor();
    RefundProcessor createRefundProcessor();
    PaymentValidator createPaymentValidator();
}

// Razorpay Concrete Products
class RazorpayPaymentProcessor implements PaymentProcessor {

    @Override
    public void pay(double amount) {
        System.out.println("Payment of ₹" + amount + " processed using Razorpay");
    }
}

class RazorpayRefundProcessor implements RefundProcessor {

    @Override
    public void refund(String transactionId) {
        System.out.println("Refund processed using Razorpay for transaction: "+ transactionId);
    }
}

class RazorpayPaymentValidator implements PaymentValidator {

    @Override
    public boolean validate(double amount) {
        System.out.println("Validating Razorpay payment...");
        return amount > 0;
    }
}

// Razorpay Concrete Factory
class RazorpayFactory implements PaymentFactory {

    @Override
    public PaymentProcessor createPaymentProcessor() {
        return new RazorpayPaymentProcessor();
    }

    @Override
    public RefundProcessor createRefundProcessor() {
        return new RazorpayRefundProcessor();
    }

    @Override
    public PaymentValidator createPaymentValidator() {
        return new RazorpayPaymentValidator();
    }
}

class StripePaymentProcessor implements PaymentProcessor {

    @Override
    public void pay(double amount) {
        System.out.println("Payment of $" + amount + " processed using Stripe");
    }
}

class StripeRefundProcessor implements RefundProcessor {

    @Override
    public void refund(String transactionId) {
        System.out.println("Refund processed using Stripe for transaction: " + transactionId);
    }
}

class StripePaymentValidator implements PaymentValidator {

    @Override
    public boolean validate(double amount) {
        System.out.println("Validating Stripe payment...");
        return amount > 0;
    }
}

// Stripe Concrete Factory
class StripeFactory implements PaymentFactory {

    @Override
    public PaymentProcessor createPaymentProcessor() {
        return new StripePaymentProcessor();
    }

    @Override
    public RefundProcessor createRefundProcessor() {
        return new StripeRefundProcessor();
    }

    @Override
    public PaymentValidator createPaymentValidator() {
        return new StripePaymentValidator();
    }
}

// Client
class PaymentService {
    
    private PaymentFactory paymentFactory;

    public PaymentService(PaymentFactory paymentFactory) {
        this.paymentFactory = paymentFactory;
    }

    public void processPayment(double amount) {
        PaymentValidator validator = paymentFactory.createPaymentValidator();
        if (validator.validate(amount)) {
            PaymentProcessor processor = paymentFactory.createPaymentProcessor();
            processor.pay(amount);
        } else {
            System.out.println("Invalid payment amount: " + amount);
        }
    }

    public void processRefund(String transactionId) {
        RefundProcessor refundProcessor = paymentFactory.createRefundProcessor();
        refundProcessor.refund(transactionId);
    }
}


public class AbstractFactoryDesignPattern {

    public static void main(String[] args) {

        // Razorpay family
        PaymentFactory razorpayFactory = new RazorpayFactory();

        PaymentService paymentService = new PaymentService(razorpayFactory);

        paymentService.processPayment(5000);

        paymentService.processRefund("TXN123");

        System.out.println("-------------------------");


        // Stripe family
        PaymentFactory stripeFactory = new StripeFactory();

        paymentService = new PaymentService(stripeFactory);

        paymentService.processPayment(3000);

        paymentService.processRefund("TXN456");
    }
    
}
