import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Producer_Consumer_Demo {
    Queue<Integer> queue = new LinkedList<>();
    int bufferSize;

    public Producer_Consumer_Demo(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    synchronized void produce(int itemProduced) throws InterruptedException {
        while (queue.size() == bufferSize) {
            wait();
        }
        queue.add(itemProduced);
        System.out.println("Produced: " + itemProduced);
        notifyAll();
    }

    synchronized int consume() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        int value = queue.remove();
        System.out.println("Consumed: " + value);
        notifyAll();
        return value;
    }
   
}

public class Producer_Consumer {
    public static void main(String[] args) {
        Producer_Consumer_Demo pc = new Producer_Consumer_Demo(3);

        ExecutorService producerConsumer = Executors.newFixedThreadPool(1);

        ExecutorService consumerThread = Executors.newFixedThreadPool(1);

   
        for (int i = 0; i < 5; i++) {
            final int item = i;
            producerConsumer.submit(() -> {
                try {
                    pc.produce(item);
                    Thread.sleep(3000); // Simulate time taken to produce an item
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
  
        for (int i = 0; i < 5; i++) {
            consumerThread.submit(() -> {
                try {
                    pc.consume();
                    Thread.sleep(6000); // Simulate time taken to consume an item
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        producerConsumer.shutdown();
        consumerThread.shutdown();

        try {
            producerConsumer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            consumerThread.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        
    }
    

}
