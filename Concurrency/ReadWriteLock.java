import java.util.HashMap;
import java.util.Map;

class MyReadWriteLock {
    private int readers = 0;
    private int waitingWriters = 0;
    private boolean activeWriter = false;

    public synchronized void readLock() throws InterruptedException {
        while (activeWriter || waitingWriters > 0) {
            wait();
        }
        readers++;
    }

    public synchronized void releaseReadLock() {
        if (readers == 0) {
            throw new IllegalMonitorStateException("No reader to release.");
        }

        readers--;
        if (readers == 0) {
            notifyAll();
        }
    }

    public synchronized void writeLock() throws InterruptedException {
        waitingWriters++;
        try {
            while (activeWriter || readers > 0) {
                wait();
            }
            activeWriter = true;
        } finally {
            waitingWriters--;
        }
    }

    public synchronized void releaseWriteLock() {
        if (!activeWriter) {
            throw new IllegalMonitorStateException("No active writer to release.");
        }

        activeWriter = false;
        notifyAll();
    }
}

class Cache {
    private final Map<Integer, Integer> map = new HashMap<>();
    private final MyReadWriteLock lock = new MyReadWriteLock();

    public void put(Integer key, Integer value) throws InterruptedException {
        lock.writeLock();
        try {
            map.put(key, value);
        } finally {
            lock.releaseWriteLock();
        }
    }

    public Integer get(Integer key) throws InterruptedException {
        lock.readLock();
        try {
            return map.get(key);
        } finally {
            lock.releaseReadLock();
        }
    }
}

public class ReadWriteLock {
    public static void main(String[] args) throws InterruptedException {
        Cache cache = new Cache();

        cache.put(1, 100);
        System.out.println("Value for key 1: " + cache.get(1));
    }
}
