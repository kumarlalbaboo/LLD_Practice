class Elevator implements Runnable {

    @Override
    public void run() {
        System.out.println("Elevator is running...");
    }
}

public class ConcurrencyBasics {

    public static void main(String[] args) {
        
        Elevator elevator = new Elevator();
        Thread elevatorThread = new Thread(elevator, "Elevator-1");
        elevatorThread.start();

        Runnable task1 = () -> {
            for (int i = 0; i < 10; i++) {
                System.out.println("Task 1 - Count: " + i);
                try {
                    Thread.sleep(500); // Sleep for 500 milliseconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        Runnable task2 = () -> {
            for (int i = 0; i < 10; i++) {
                System.out.println("Task 2 - Count: " + i);
                try {
                    Thread.sleep(300); // Sleep for 300 milliseconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread thread1 = new Thread(task1, "Thread-1");
        thread1.start();

        Thread thread2 = new Thread(task2, "Thread-2");
        thread2.start();


        System.out.println("Main thread is doing other work...");   

    }
    
}
