
// Double check singleton class
class DoubleCheckSingleton{

    private static volatile DoubleCheckSingleton instance;

    private DoubleCheckSingleton(){}

    public static DoubleCheckSingleton getInstance(){
        
        //Secound check (Synchronized)
        if(instance == null){
            // First check (Not synchronized)
            synchronized(DoubleCheckSingleton.class){
                // Second check (Synchronized)
                if(instance == null){
                    instance = new DoubleCheckSingleton();
                }
            }
        }
        // Return the instance (Either newly created or existing)
        return instance;
    }
}

// Bill Pugh Singleton class
class LazzyInnerClassSingleton {

    private LazzyInnerClassSingleton(){};

    private static class SingletonHelper {
        private static final LazzyInnerClassSingleton instance = new LazzyInnerClassSingleton();
    }

    public static LazzyInnerClassSingleton getInstance(){
        return SingletonHelper.instance;
    }

}

/**
 * SingletonDesignPattern
 */
public class SingletonDesignPattern {
    public static void main(String[] args) {
        System.out.println("Double check Singleton class");
        DoubleCheckSingleton obj1 = DoubleCheckSingleton.getInstance();
        System.out.println(obj1.hashCode());

        System.out.println("Bill Pugh Singleton class");
        LazzyInnerClassSingleton obj2 = LazzyInnerClassSingleton.getInstance();
        System.out.println(obj2.hashCode());
    }
    
}

/*
✅ Key Points:
Private constructor – prevents external instantiation.
Static instance – holds the single object.
Public method – returns the instance.

Bill Pugh Singleton:

Eager Singleton --> Not Lazy
Synchronized Singleton --> Slow
Double-checked locking --> complex & error -prone

Bill Pugh Singleton is one of the most important singleton variants — clean, lazy, thread-safe without synchronization.

Bill Pugh Singleton uses a static inner helper class to achieve lazy initialization and thread safety by
leveraging JVM class loading guarantees,without using synchronization.”

*/