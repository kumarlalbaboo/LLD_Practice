import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * ============================================================
 *                 ELEVATOR SYSTEM - LLD
 * ============================================================
 *
 * Functional Requirements:
 * 1. Support multiple elevators
 * 2. Support external elevator requests
 * 3. Support internal elevator requests
 * 4. Support multiple scheduling algorithms
 * 5. Each elevator should operate independently
 * 6. Display current elevator status
 * 7. Thread-safe
 * 8. Graceful shutdown System
 *
 * Non-Functional Requirements:
 * 1. Extensible
 * 2. Maintainable
 * 3. Thread-safe
 * 4. Low waiting time
 * 5. Follow SOLID principles
 *
 * Design Patterns:
 * - Strategy Pattern -> SchedulingAlgorithm
 * - Composition -> ElevatorSystem contains Elevators
 * - Producer/Consumer style -> wait()/notifyAll()
 *
 * ============================================================
 */


 //-----------ENUMS-----------------------------------------------------

enum ElevatorState {
    IDLE,
    MOVING,
    STOPPED,
    MAINTENANCE
}

enum Direction {
    UP,
    DOWN,
    IDLE
}

enum RequestType {
    INTERNAL,
    EXTERNAL
}


 //---------------ELEVATOR REQUEST---------------------------------------

class ElevatorRequest {

    private final int floor;
    private final Direction direction;
    private final RequestType requestType;

    public ElevatorRequest(int floor, Direction direction, RequestType requestType) {
        this.floor = floor;
        this.direction = direction;
        this.requestType = requestType;
    }

    public int getFloor() {
        return floor;
    }

    public Direction getDirection() {
        return direction;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    @Override
    public String toString() {
        return "ElevatorRequest{" + "floor=" + floor + ", direction=" + direction + ", requestType=" + requestType +'}';
    }
}


/*
 * ==================SCHEDULING STRATEGY========================
 * Strategy Pattern
 *
 * ElevatorSystem does not know HOW an elevator is selected.
 * It delegates that responsibility to SchedulingAlgorithm.
 */

interface SchedulingAlgorithm {

    Elevator assignElevator(ElevatorRequest request, Collection<Elevator> elevators);

}


/*
 * ===============NEAREST ELEVATOR STRATEGY====================
 * Priority:
 *
 * 1. Idle elevator
 * 2. Elevator moving in same direction and request is on its way
 * 3. Otherwise nearest elevator
 */

class NearestElevatorFirst implements SchedulingAlgorithm {

    @Override
    public Elevator assignElevator(ElevatorRequest request, Collection<Elevator> elevators) {

        Elevator bestElevator = null;
        int bestDistance = Integer.MAX_VALUE;

        /*
         * First preference:
         *
         * Idle elevator OR elevator moving in the same directionand request is on its way.
         */

        for (Elevator elevator : elevators) {

            if (!elevator.isAvailableForAssignment()) {
                continue;
            }

            int distance = Math.abs(elevator.getCurrentFloor() - request.getFloor());

            Direction elevatorDirection = elevator.getDirection();

            boolean suitable = elevatorDirection == Direction.IDLE || (elevatorDirection == request.getDirection() && isOnTheWay(elevator, request));

            if (suitable && distance < bestDistance) {

                bestDistance = distance;
                bestElevator = elevator;
            }
        }

        // If no ideal elevator exists, choose the closest available elevator.

        if (bestElevator == null) {

            bestDistance = Integer.MAX_VALUE;

            for (Elevator elevator : elevators) {

                if (!elevator.isAvailableForAssignment()) {
                    continue;
                }

                int distance = Math.abs(elevator.getCurrentFloor() - request.getFloor());

                if (distance < bestDistance) {

                    bestDistance = distance;
                    bestElevator = elevator;
                }
            }
        }

        return bestElevator;
    }


    private boolean isOnTheWay(Elevator elevator,ElevatorRequest request) {

        int currentFloor = elevator.getCurrentFloor();

        if (request.getDirection() == Direction.UP) {

            return request.getFloor() >= currentFloor;
        }

        if (request.getDirection() == Direction.DOWN) {

            return request.getFloor() <= currentFloor;
        }

        return true;
    }
}

//----------------------ELEVATOR---------------------------------------

class Elevator implements Runnable {

    private final int id;

    /*
     * PriorityQueue for upward destinations.
     *
     * Example:
     *
     * add 10
     * add 5
     * add 8
     *
     * poll() -> 5
     */

    private final PriorityQueue<Integer> upStops = new PriorityQueue<>();


    /*
     * PriorityQueue for downward destinations.
     *
     * Reverse order:
     *
     * add 10
     * add 5
     * add 8
     *
     * poll() -> 10
     */

    private final PriorityQueue<Integer> downStops = new PriorityQueue<>(Comparator.reverseOrder());


    /*
     * Single lock protects all mutable state:
     *
     * - currentFloor
     * - direction
     * - state
     * - queues
     */

    private final Object lock = new Object();


    private int currentFloor;

    private Direction direction = Direction.IDLE;

    private ElevatorState state = ElevatorState.IDLE;

    // Volatile because shutdown() can be called from another thread.

    private volatile boolean running;


    public Elevator(int id, int initialFloor) {

        if (initialFloor < 0) {
            throw new IllegalArgumentException("Floor cannot be negative");
        }

        this.id = id;
        this.currentFloor = initialFloor;
    }

    //----------------GETTERS--------------------------------------
    
    public int getId() {

        return id;
    }

    public int getCurrentFloor() {

        synchronized (lock) {
            return currentFloor;
        }
    }

    public Direction getDirection() {

        synchronized (lock) {
            return direction;
        }
    }

    public ElevatorState getState() {

        synchronized (lock) {
            return state;
        }
    }

    //--------------AVAILABILITY CHECK-------------------------

    public boolean isAvailableForAssignment() {

        synchronized (lock) {

            return state != ElevatorState.MAINTENANCE;
        }
    }


    /*----------------------EXTERNAL REQUEST--------------------------
        * Example:
        *
        * Passenger is on floor 5
        * Passenger wants to go UP
        *
        * ElevatorSystem -> Scheduler
        * Scheduler -> Elevator
        * Elevator -> addStop(5)
    */

    public void assignExternalRequest(ElevatorRequest request) {

        if (request.getRequestType() != RequestType.EXTERNAL) {

            throw new IllegalArgumentException("Only EXTERNAL requests are allowed here");
        }

        addStop(request.getFloor());
    }


    /*----------------------INTERNAL REQUEST--------------------------
     * Passenger is already inside elevator.
     *
     * Example:
     *
     * Current floor = 5
     * Passenger presses 10
     *
     * Elevator -> addStop(10)
     */

    public void addInternalRequest(int destinationFloor) {

        addStop(destinationFloor);
    }

    //------------------ADD STOP------------------------------

    private void addStop(int floor) {

        synchronized (lock) {

            /*
             * Same floor:
             *
             * Elevator is already here.
             * No need to move.
             */

            if (floor == currentFloor) {

                state = ElevatorState.STOPPED;

                System.out.println("Elevator " + id +" is already at floor " + currentFloor);

                openDoorsInternal();

                return;
            }

            //Elevator is moving UP.

            if (floor > currentFloor) {

                upStops.offer(floor);
            }

            // Elevator is moving DOWN.

            else {

                downStops.offer(floor);
            }

            //If elevator is idle, decide initial direction.

            if (direction == Direction.IDLE) {

                if (floor > currentFloor) {

                    direction = Direction.UP;
                }

                else {

                    direction = Direction.DOWN;
                }

                state = ElevatorState.MOVING;
            }


            /*
             * Wake up elevator thread.
             */

            lock.notifyAll();
        }
    }

    //-----------------RUN METHOD------------------------------

    @Override
    public void run() {

        running = true;

        System.out.println("Elevator " + id + " started.");

        while (running) {

            waitForRequest();

            if (!running) {
                break;
            }

            /*
             * SCAN-like scheduling.
             *
             * If moving UP:
             *      serve UP requests first.
             *
             * Then:
             *      serve DOWN requests.
             *
             * If moving DOWN:
             *      serve DOWN requests first.
             *
             * Then:
             *      serve UP requests.
             */

            if (getDirection() == Direction.UP) {

                serveUp();

                serveDown();
            }

            else if (getDirection() == Direction.DOWN) {

                serveDown();

                serveUp();
            }

            else {

                /*
                 * Direction is IDLE.
                 * Determine which queue has work.
                 */

                chooseDirection();
            }
        }

        System.out.println("Elevator " + id + " stopped.");
    }

    //----------------WAIT FOR REQUEST--------------------------//
 
    private void waitForRequest() {

        synchronized (lock) {

            while (running && upStops.isEmpty() && downStops.isEmpty()) {

                direction = Direction.IDLE;
                state = ElevatorState.IDLE;

                try {

                    lock.wait();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    running = false;

                    return;
                }
            }
        }
    }

    //--------------CHOOSE INITIAL DIRECTION--------------------------//

    private void chooseDirection() {

        synchronized (lock) {

            if (!upStops.isEmpty()) {

                direction = Direction.UP;
                state = ElevatorState.MOVING;
            }

            else if (!downStops.isEmpty()) {

                direction = Direction.DOWN;
                state = ElevatorState.MOVING;
            }

            else {

                direction = Direction.IDLE;
                state = ElevatorState.IDLE;
            }
        }
    }

    /*------------------SERVE UP------------------------------
     * UP queue:
     *
     * 3
     * 5
     * 8
     * 12
     *
     * Elevator serves:
     *
     * 3 -> 5 -> 8 -> 12
     */

    private void serveUp() {

        synchronized (lock) {

            if (upStops.isEmpty()) {

                return;
            }

            direction = Direction.UP;
            state = ElevatorState.MOVING;
        }


        while (running) {

            Integer nextStop;

            synchronized (lock) {

                nextStop = upStops.peek();

                if (nextStop == null) {

                    break;
                }
            }

            //Move until we reach destination.

            if (getCurrentFloor() < nextStop) {

                moveOneFloor();

            }

            else {

                //Destination reached.

                synchronized (lock) {

                    upStops.poll();

                    state = ElevatorState.STOPPED;
                }

                openDoors();

                //After opening doors, continue serving next request.

            }
        }
    }

    //------------------------SERVE DOWN--------------------------

    private void serveDown() {

        synchronized (lock) {

            if (downStops.isEmpty()) {

                return;
            }

            direction = Direction.DOWN;
            state = ElevatorState.MOVING;
        }


        while (running) {

            Integer nextStop;

            synchronized (lock) {

                nextStop = downStops.peek();

                if (nextStop == null) {

                    break;
                }
            }

            //Move down.
        
            if (getCurrentFloor() > nextStop) {

                moveOneFloor();

            }

            else {

                //Destination reached.

                synchronized (lock) {

                    downStops.poll();

                    state = ElevatorState.STOPPED;
                }

                openDoors();
            }
        }
    }

    //-------------------MOVE ONE FLOOR--------------------------

    private void moveOneFloor() {

        synchronized (lock) {

            if (!running) {
                return;
            }

            state = ElevatorState.MOVING;

            if (direction == Direction.UP) {

                currentFloor++;

            }

            else if (direction == Direction.DOWN) {

                currentFloor--;
            }

            System.out.println("Elevator " + id + " moved to floor " + currentFloor + " [" + direction + "]");
        }

        //Simulate movement time.

        try {

            Thread.sleep(500);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            running = false;
        }
    }

    //--------------------OPEN DOORS--------------------------

    private void openDoors() {

        synchronized (lock) {

            if (!running) {
                return;
            }

            state = ElevatorState.STOPPED;

            System.out.println("Elevator " + id + " opening doors at floor " + currentFloor);
        }

        //Simulate door opening.

        try {

            Thread.sleep(300);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            running = false;
        }
    }


    /*
     * Used for same-floor requests.
     *
     * No extra synchronization required because
     * caller already holds lock.
     */

    private void openDoorsInternal() {

        System.out.println("Elevator " + id +" opening doors at floor " +currentFloor);

        state = ElevatorState.STOPPED;

        state = ElevatorState.IDLE;
        direction = Direction.IDLE;
    }

    //---------------------STATUS------------------------------
    
    public String getStatus() {

        synchronized (lock) {

            return String.format(
                    "Elevator %d | Floor: %d | Direction: %s | State: %s | UP Queue: %s | DOWN Queue: %s",
                    id,
                    currentFloor,
                    direction,
                    state,
                    upStops,
                    downStops
            );
        }
    }

    //-------------------SHUTDOWN--------------------------

    public void shutdown() {

        synchronized (lock) {

            running = false;

            lock.notifyAll();
        }
    }

    //-----------------MAINTENANCE MODE--------------------------

    public void putIntoMaintenance() {

        synchronized (lock) {

            state = ElevatorState.MAINTENANCE;

            System.out.println("Elevator " + id + " is now in maintenance mode.");
        }
    }


    public void removeFromMaintenance() {

        synchronized (lock) {

            state = ElevatorState.IDLE;

            System.out.println("Elevator " + id + " removed from maintenance mode.");

            lock.notifyAll();
        }
    }
}


/*               
 * ======================DISPLAY SERVICE==========================
 *
 * Single Responsibility:
 *
 * This class is responsible only for displaying elevator information.
 */

class ElevatorDisplay {

    public void display(Collection<Elevator> elevators) {

        System.out.println();
        System.out.println("========== ELEVATOR STATUS ==========");

        for (Elevator elevator : elevators) {

            System.out.println(elevator.getStatus());
        }

        System.out.println("=====================================");

        System.out.println();
    }
}



 /*=====================ELEVATOR SYSTEM=========================
 *
 * Main entry point.
 *
 * Responsibilities:
 *
 * 1. Manage elevators
 * 2. Receive external requests
 * 3. Delegate elevator selection to scheduler
 * 4. Handle internal requests
 * 5. Start/stop elevator threads
 */

class ElevatorSystem {

    /*
     * ConcurrentHashMap provides thread-safe access to elevators.
     *
     * elevatorId -> Elevator
     */

    private final Map<Integer, Elevator> elevators = new ConcurrentHashMap<>();

    //Each elevator has its own thread.

    private final Map<Integer, Thread> elevatorThreads = new ConcurrentHashMap<>();


    private final SchedulingAlgorithm schedulingAlgorithm;

    private final ElevatorDisplay display;


    private final AtomicBoolean started = new AtomicBoolean(false);


    public ElevatorSystem(SchedulingAlgorithm schedulingAlgorithm) {

        this.schedulingAlgorithm = Objects.requireNonNull(schedulingAlgorithm, "Scheduling algorithm cannot be null");

        this.display = new ElevatorDisplay();
    }

    //-------------------START SYSTEM--------------------------//

    public void start(int elevatorCount) {

        if (elevatorCount <= 0) {

            throw new IllegalArgumentException("Elevator count must be greater than zero");
        }

        if (!started.compareAndSet(false, true)) {

            throw new IllegalStateException( "Elevator system is already started");
        }

        for (int i = 0; i < elevatorCount; i++) {

            Elevator elevator = new Elevator(i, 0);

            Thread thread = new Thread(elevator, "Elevator-" + i);

            elevators.put(elevator.getId(), elevator);

            elevatorThreads.put(elevator.getId(), thread);

            thread.start();
        }

        System.out.println(elevatorCount + " elevators started.");
    }


    /*           
     * ====================EXTERNAL REQUEST======================
     *
     * Example:
     *
     * Floor 5
     * Direction UP
     *
     * Scheduler chooses elevator.
     */

    public void requestElevator(int floor, Direction direction) {

        validateFloor(floor);

        ElevatorRequest request = new ElevatorRequest(floor, direction, RequestType.EXTERNAL);

        Elevator elevator = schedulingAlgorithm.assignElevator(request,elevators.values());

        if (elevator == null) {

            System.out.println("No elevator available for request: "+ request);

            return;
        }

        System.out.println("[EXTERNAL] Floor " + floor + " wants " + direction + " -> Elevator " + elevator.getId());

        elevator.assignExternalRequest(request);
    }


    /*           
     * ========================== INTERNAL REQUEST==============================
     *
     * Passenger is already inside elevator.
     *
     * No scheduler required.
     */

    public void pressFloorButton(int elevatorId, int destinationFloor) {

        validateFloor(destinationFloor);

        Elevator elevator = elevators.get(elevatorId);

        if (elevator == null) {

            System.out.println("Elevator " + elevatorId + " does not exist.");

            return;
        }


        System.out.println("[INTERNAL] Elevator " + elevatorId + " -> Floor " + destinationFloor);

        elevator.addInternalRequest(destinationFloor);

    }

    //-------------------DISPLAY STATUS----------------------//

    public void displayStatus() {

        display.display(elevators.values());
    }

    //---------------------MAINTENANCE------------------------//

    public void putElevatorInMaintenance(int elevatorId) {

        Elevator elevator = elevators.get(elevatorId);


        if (elevator == null) {

            System.out.println("Elevator " + elevatorId + " does not exist.");

            return;
        }

        elevator.putIntoMaintenance();
    }

    public void removeElevatorFromMaintenance(int elevatorId) {

        Elevator elevator = elevators.get(elevatorId);


        if (elevator == null) {

            System.out.println("Elevator " + elevatorId + " does not exist.");

            return;
        }

        elevator.removeFromMaintenance();
    }

    //----------------------SHUTDOWN--------------------------//

    public void shutdown() {

        System.out.println("Shutting down elevator system...");

        /*
         * Tell every elevator to stop.
         */

        for (Elevator elevator : elevators.values()) {

            elevator.shutdown();
        }


        /*
         * Wait for elevator threads to finish.
         */

        for (Thread thread : elevatorThreads.values()) {

            try {

                thread.join();

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                break;
            }
        }

        started.set(false);

        System.out.println("Elevator system stopped.");
    }

    //--------------------VALIDATION--------------------------//
 
    private void validateFloor(int floor) {

        if (floor < 0) {

            throw new IllegalArgumentException( "Floor cannot be negative");
        }
    }
}

                 
//=============================MAIN===============================//

public class ElevatorSystemSolution {

    public static void main(String[] args) throws InterruptedException {

        //=====================MAINCREATE ELEVATOR SYSTEM=====================//

        ElevatorSystem system = new ElevatorSystem(new NearestElevatorFirst());

        //========================START 3 ELEVATORS============================//

        system.start(3);

        /*
         * EXTERNAL REQUEST #1
         *
         * Passenger on floor 5
         * Wants to go UP
         */

        system.requestElevator(5, Direction.UP);

        /*
         * EXTERNAL REQUEST #2
         *
         * Passenger on floor 3
         * Wants to go DOWN
         */

        system.requestElevator(3,Direction.DOWN);

        /*
         * EXTERNAL REQUEST #3
         *
         * Passenger on floor 10
         * Wants to go DOWN
         */

        system.requestElevator(10,Direction.DOWN);

        //Let elevators move.
        Thread.sleep(4000);

        /*
         * INTERNAL REQUEST
         *
         * Passenger entered Elevator 0
         * and presses floor 8.
         */

        system.pressFloorButton(0,8);

        //Another internal request.
        system.pressFloorButton(0, 12);

        //Display current status.
        Thread.sleep(2000);

        system.displayStatus();

        //==============MAINTENANCE EXAMPLE==============//

        system.putElevatorInMaintenance(2);


        Thread.sleep(1000);

        system.displayStatus();

        //Remove maintenance mode.
        system.removeElevatorFromMaintenance(2);

        //Let system continue.
        Thread.sleep(5000);

        //Display final status.
        system.displayStatus();

        //==================SHUTDOWN==================//
        system.shutdown();

    }
}