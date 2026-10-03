import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/*
 * ============================================================
 * ENUMS
 * ============================================================
 */

enum VehicleType {
    SMALL,
    MEDIUM,
    LARGE
}

enum SlotType {
    SMALL,
    MEDIUM,
    LARGE
}

enum TicketStatus {
    ACTIVE,
    PAYMENT_SUCCESS,
    PAID,
    CANCELLED
}

enum PaymentStatus {
    SUCCESS,
    FAILED
}


/*
 * ============================================================
 * VEHICLE
 * ============================================================
 */

class Vehicle {

    private final String licensePlate;
    private final VehicleType vehicleType;

    public Vehicle(String licensePlate, VehicleType vehicleType) {

        if (licensePlate == null ||
                licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "License plate cannot be empty");
        }

        if (vehicleType == null) {
            throw new IllegalArgumentException(
                    "Vehicle type cannot be null");
        }

        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    @Override
    public String toString() {
        return licensePlate + " [" + vehicleType + "]";
    }
}


/*
 * ============================================================
 * SLOT
 * ============================================================
 */

class Slot {

    private final String slotId;
    private final int floorNumber;
    private final SlotType slotType;

    private Vehicle vehicle;

    public Slot(
            String slotId,
            int floorNumber,
            SlotType slotType) {

        if (slotId == null || slotId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Slot ID cannot be empty");
        }

        if (floorNumber <= 0) {
            throw new IllegalArgumentException(
                    "Floor number must be positive");
        }

        if (slotType == null) {
            throw new IllegalArgumentException(
                    "Slot type cannot be null");
        }

        this.slotId = slotId;
        this.floorNumber = floorNumber;
        this.slotType = slotType;
    }

    public String getSlotId() {
        return slotId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public synchronized boolean isFree() {
        return vehicle == null;
    }

    /*
     * Atomic check + occupy.
     */
    public synchronized boolean occupy(Vehicle vehicle) {

        Objects.requireNonNull(
                vehicle,
                "Vehicle cannot be null");

        if (this.vehicle != null) {
            return false;
        }

        this.vehicle = vehicle;
        return true;
    }

    /*
     * Release only if the same vehicle is parked.
     */
    public synchronized boolean release(Vehicle vehicle) {

        if (this.vehicle == null) {
            return false;
        }

        if (!this.vehicle.getLicensePlate()
                .equals(vehicle.getLicensePlate())) {
            return false;
        }

        this.vehicle = null;
        return true;
    }

    public synchronized Vehicle getVehicle() {
        return vehicle;
    }

    @Override
    public synchronized String toString() {

        return "Slot{" +
                "slotId='" + slotId + '\'' +
                ", floor=" + floorNumber +
                ", slotType=" + slotType +
                ", free=" + (vehicle == null) +
                '}';
    }
}


/*
 * ============================================================
 * LEVEL
 * ============================================================
 */

class Level {

    private final int floorNumber;
    private final List<Slot> slots;

    public Level(
            int floorNumber,
            List<Slot> slots) {

        if (floorNumber <= 0) {
            throw new IllegalArgumentException(
                    "Floor number must be positive");
        }

        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Level must contain at least one slot");
        }

        this.floorNumber = floorNumber;

        this.slots = Collections.unmodifiableList(
                new ArrayList<>(slots));
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public List<Slot> getSlots() {
        return slots;
    }
}


/*
 * ============================================================
 * TICKET
 * ============================================================
 */

class Ticket {

    private final String ticketId;
    private final Vehicle vehicle;
    private final Slot slot;
    private final LocalDateTime entryTime;

    private volatile TicketStatus status;

    /*
     * Stores successful payment.
     *
     * This allows retry/recovery without charging again.
     */
    private PaymentResult paymentResult;

    public Ticket(
            String ticketId,
            Vehicle vehicle,
            Slot slot,
            LocalDateTime entryTime) {

        this.ticketId = Objects.requireNonNull(
                ticketId,
                "Ticket ID cannot be null");

        this.vehicle = Objects.requireNonNull(
                vehicle,
                "Vehicle cannot be null");

        this.slot = Objects.requireNonNull(
                slot,
                "Slot cannot be null");

        this.entryTime = Objects.requireNonNull(
                entryTime,
                "Entry time cannot be null");

        this.status = TicketStatus.ACTIVE;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Slot getSlot() {
        return slot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public synchronized TicketStatus getStatus() {
        return status;
    }

    public synchronized PaymentResult getPaymentResult() {
        return paymentResult;
    }

    /*
     * ACTIVE -> PAYMENT_SUCCESS
     */
    public synchronized void markPaymentSuccess(
            PaymentResult paymentResult) {

        Objects.requireNonNull(
                paymentResult,
                "Payment result cannot be null");

        if (status != TicketStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot mark payment successful from state: "
                            + status);
        }

        this.paymentResult = paymentResult;
        this.status = TicketStatus.PAYMENT_SUCCESS;
    }

    /*
     * PAYMENT_SUCCESS -> PAID
     */
    public synchronized void markPaid() {

        if (status != TicketStatus.PAYMENT_SUCCESS) {
            throw new IllegalStateException(
                    "Cannot mark ticket paid from state: "
                            + status);
        }

        status = TicketStatus.PAID;
    }

    /*
     * ACTIVE -> CANCELLED
     */
    public synchronized void cancel() {

        if (status != TicketStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot cancel ticket from state: "
                            + status);
        }

        status = TicketStatus.CANCELLED;
    }
}


/*
 * ============================================================
 * SLOT ALLOCATION STRATEGY
 * ============================================================
 */

interface SlotAllocationStrategy {

    Slot allocate(
            List<Level> levels,
            Vehicle vehicle);
}


/*
 * ============================================================
 * SLOT RANK UTILITY
 * ============================================================
 */

final class SlotRanking {

    private SlotRanking() {
    }

    public static int slotRank(SlotType type) {

        switch (type) {

            case SMALL:
                return 1;

            case MEDIUM:
                return 2;

            case LARGE:
                return 3;

            default:
                throw new IllegalArgumentException(
                        "Unknown slot type");
        }
    }

    public static int vehicleRank(VehicleType type) {

        switch (type) {

            case SMALL:
                return 1;

            case MEDIUM:
                return 2;

            case LARGE:
                return 3;

            default:
                throw new IllegalArgumentException(
                        "Unknown vehicle type");
        }
    }

    public static boolean isCompatible(
            Slot slot,
            Vehicle vehicle) {

        return slotRank(slot.getSlotType())
                >= vehicleRank(vehicle.getVehicleType());
    }
}


/*
 * ============================================================
 * FIRST AVAILABLE STRATEGY
 * ============================================================
 */

class FirstAvailableAllocationStrategy
        implements SlotAllocationStrategy {

    @Override
    public Slot allocate(
            List<Level> levels,
            Vehicle vehicle) {

        Objects.requireNonNull(vehicle);

        for (Level level : levels) {

            for (Slot slot : level.getSlots()) {

                if (!SlotRanking.isCompatible(
                        slot,
                        vehicle)) {
                    continue;
                }

                /*
                 * occupy() performs atomic
                 * check + assignment.
                 */
                if (slot.occupy(vehicle)) {
                    return slot;
                }
            }
        }

        throw new IllegalStateException(
                "No suitable parking slot available");
    }
}


/*
 * ============================================================
 * BEST FIT STRATEGY
 * ============================================================
 */

class BestFitAllocationStrategy
        implements SlotAllocationStrategy {

    @Override
    public Slot allocate(
            List<Level> levels,
            Vehicle vehicle) {

        Objects.requireNonNull(vehicle);

        List<Slot> candidates =
                new ArrayList<>();

        for (Level level : levels) {

            for (Slot slot : level.getSlots()) {

                if (SlotRanking.isCompatible(
                        slot,
                        vehicle)
                        && slot.isFree()) {

                    candidates.add(slot);
                }
            }
        }

        /*
         * Prefer smallest suitable slot.
         */
        candidates.sort(
                Comparator
                        .<Slot>comparingInt(
                                slot -> SlotRanking.slotRank(
                                        slot.getSlotType()))
                        .thenComparingInt(
                                Slot::getFloorNumber)
                        .thenComparing(
                                Slot::getSlotId)
        );

        /*
         * isFree() above is only a candidate check.
         *
         * occupy() is the actual atomic operation.
         */
        for (Slot slot : candidates) {

            if (slot.occupy(vehicle)) {
                return slot;
            }
        }

        throw new IllegalStateException(
                "No suitable parking slot available");
    }
}


/*
 * ============================================================
 * FEE CALCULATION STRATEGY
 * ============================================================
 */

interface FeeCalculationStrategy {

    BigDecimal calculateFee(Ticket ticket);
}


/*
 * ============================================================
 * HOURLY FEE
 * ============================================================
 */

class HourlyFeeCalculationStrategy
        implements FeeCalculationStrategy {

    private final BigDecimal hourlyRate;

    public HourlyFeeCalculationStrategy(
            BigDecimal hourlyRate) {

        if (hourlyRate == null ||
                hourlyRate.compareTo(
                        BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Hourly rate must be greater than zero");
        }

        this.hourlyRate = hourlyRate;
    }

    @Override
    public BigDecimal calculateFee(
            Ticket ticket) {

        Objects.requireNonNull(ticket);

        long minutes =
                Duration.between(
                        ticket.getEntryTime(),
                        LocalDateTime.now()
                ).toMinutes();

        /*
         * Minimum one hour.
         */
        long hours = Math.max(
                1,
                (minutes + 59) / 60
        );

        return hourlyRate.multiply(
                BigDecimal.valueOf(hours)
        );
    }
}


/*
 * ============================================================
 * FIXED FEE
 * ============================================================
 */

class FixedFeeCalculationStrategy
        implements FeeCalculationStrategy {

    private final BigDecimal fixedFee;

    public FixedFeeCalculationStrategy(
            BigDecimal fixedFee) {

        if (fixedFee == null ||
                fixedFee.compareTo(
                        BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Fixed fee must be greater than zero");
        }

        this.fixedFee = fixedFee;
    }

    @Override
    public BigDecimal calculateFee(
            Ticket ticket) {

        Objects.requireNonNull(ticket);

        return fixedFee;
    }
}


/*
 * ============================================================
 * PAYMENT
 * ============================================================
 */

interface PaymentService {

    PaymentResult pay(
            String ticketId,
            String idempotencyKey,
            BigDecimal amount);
}


/*
 * ============================================================
 * PAYMENT RESULT
 * ============================================================
 */

class PaymentResult {

    private final PaymentStatus status;
    private final String transactionId;
    private final BigDecimal amount;
    private final String idempotencyKey;

    public PaymentResult(
            PaymentStatus status,
            String transactionId,
            BigDecimal amount,
            String idempotencyKey) {

        this.status = Objects.requireNonNull(status);

        this.transactionId = Objects.requireNonNull(
                transactionId);

        this.amount = Objects.requireNonNull(amount);

        this.idempotencyKey = Objects.requireNonNull(
                idempotencyKey);
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public String toString() {

        return "PaymentResult{" +
                "status=" + status +
                ", transactionId='" +
                transactionId + '\'' +
                ", amount=" + amount +
                ", idempotencyKey='" +
                idempotencyKey + '\'' +
                '}';
    }
}


/*
 * ============================================================
 * MOCK PAYMENT SERVICE
 * ============================================================
 *
 * One shared instance must be used by all ExitGates.
 *
 * Idempotency:
 *
 * Same key + same ticket + same amount
 *      -> return existing payment
 *
 * Same key + different amount
 *      -> reject
 *
 * Same ticket + different key
 *      -> return existing successful payment
 *
 * ============================================================
 */

class MockPaymentService
        implements PaymentService {

    private final Map<String, PaymentResult>
            paymentsByIdempotencyKey =
            new ConcurrentHashMap<>();

    private final Map<String, PaymentResult>
            successfulPaymentsByTicket =
            new ConcurrentHashMap<>();

    @Override
    public PaymentResult pay(
            String ticketId,
            String idempotencyKey,
            BigDecimal amount) {

        validateInput(
                ticketId,
                idempotencyKey,
                amount);

        /*
         * First check:
         *
         * Has this exact request already succeeded?
         */
        PaymentResult existing =
                paymentsByIdempotencyKey.get(
                        idempotencyKey);

        if (existing != null) {

            validateExistingPayment(
                    existing,
                    ticketId,
                    amount);

            System.out.printf(
                    "PAYMENT | IDEMPOTENT RETRY | " +
                    "Ticket: %s | Transaction: %s%n",
                    ticketId,
                    existing.getTransactionId());

            return existing;
        }

        /*
         * Second check:
         *
         * Has this ticket already been paid?
         */
        PaymentResult existingTicketPayment =
                successfulPaymentsByTicket.get(
                        ticketId);

        if (existingTicketPayment != null) {

            if (existingTicketPayment
                    .getAmount()
                    .compareTo(amount) != 0) {

                throw new IllegalStateException(
                        "Amount mismatch for ticket "
                                + ticketId);
            }

            /*
             * Different idempotency key,
             * but ticket is already paid.
             *
             * Do NOT charge again.
             */
            return existingTicketPayment;
        }

        /*
         * Atomic payment creation.
         */
        return paymentsByIdempotencyKey.compute(
                idempotencyKey,
                (key, currentPayment) -> {

                    if (currentPayment != null) {

                        validateExistingPayment(
                                currentPayment,
                                ticketId,
                                amount);

                        return currentPayment;
                    }

                    /*
                     * Double-check ticket payment
                     * inside atomic section.
                     */
                    PaymentResult ticketPayment =
                            successfulPaymentsByTicket.get(
                                    ticketId);

                    if (ticketPayment != null) {

                        if (ticketPayment
                                .getAmount()
                                .compareTo(amount) != 0) {

                            throw new IllegalStateException(
                                    "Amount mismatch for ticket "
                                            + ticketId);
                        }

                        return ticketPayment;
                    }

                    PaymentResult result =
                            new PaymentResult(
                                    PaymentStatus.SUCCESS,
                                    UUID.randomUUID()
                                            .toString(),
                                    amount,
                                    idempotencyKey
                            );

                    successfulPaymentsByTicket.put(
                            ticketId,
                            result);

                    System.out.printf(
                            "PAYMENT | SUCCESS | " +
                            "Ticket: %s | Amount: INR %.2f | " +
                            "Transaction: %s%n",
                            ticketId,
                            amount,
                            result.getTransactionId());

                    return result;
                }
        );
    }

    private void validateInput(
            String ticketId,
            String idempotencyKey,
            BigDecimal amount) {

        if (ticketId == null ||
                ticketId.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Ticket ID cannot be empty");
        }

        if (idempotencyKey == null ||
                idempotencyKey.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Idempotency key cannot be empty");
        }

        if (amount == null ||
                amount.compareTo(
                        BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero");
        }
    }

    private void validateExistingPayment(
            PaymentResult existing,
            String ticketId,
            BigDecimal requestedAmount) {

        if (existing.getStatus()
                != PaymentStatus.SUCCESS) {

            throw new IllegalStateException(
                    "Existing payment is not successful");
        }

        if (existing.getAmount()
                .compareTo(requestedAmount) != 0) {

            throw new IllegalStateException(
                    String.format(
                            "Amount mismatch | Ticket: %s | " +
                            "Already charged: INR %.2f | " +
                            "Requested: INR %.2f",
                            ticketId,
                            existing.getAmount(),
                            requestedAmount));
        }
    }
}


/*
 * ============================================================
 * PARKING LOT
 * ============================================================
 */

class ParkingLot {

    private final List<Level> levels;

    private final List<EntryGate> entryGates =
            new ArrayList<>();

    private final List<ExitGate> exitGates =
            new ArrayList<>();

    private final SlotAllocationStrategy
            slotAllocationStrategy;

    /*
     * Active tickets indexed by license plate.
     */
    private final Map<String, Ticket>
            activeTickets =
            new ConcurrentHashMap<>();

    private final AtomicLong ticketSequence =
            new AtomicLong(1000);

    public ParkingLot(
            List<Level> levels,
            SlotAllocationStrategy slotAllocationStrategy) {

        if (levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Parking lot must contain levels");
        }

        this.slotAllocationStrategy =
                Objects.requireNonNull(
                        slotAllocationStrategy);

        this.levels =
                Collections.unmodifiableList(
                        new ArrayList<>(levels));
    }

    public void addEntryGate(
            EntryGate entryGate) {

        Objects.requireNonNull(entryGate);

        synchronized (entryGates) {
            entryGates.add(entryGate);
        }
    }

    public void addExitGate(
            ExitGate exitGate) {

        Objects.requireNonNull(exitGate);

        synchronized (exitGates) {
            exitGates.add(exitGate);
        }
    }

    /*
     * Park vehicle.
     *
     * Concurrent requests for the same vehicle
     * cannot create two active tickets.
     */
    public Ticket parkVehicle(
            Vehicle vehicle) {

        Objects.requireNonNull(vehicle);

        final Ticket[] result =
                new Ticket[1];

        activeTickets.compute(
                vehicle.getLicensePlate(),
                (licensePlate, existingTicket) -> {

                    if (existingTicket != null &&
                            existingTicket.getStatus()
                                    == TicketStatus.ACTIVE) {

                        throw new IllegalStateException(
                                "Vehicle is already parked: "
                                        + licensePlate);
                    }

                    Slot slot =
                            slotAllocationStrategy.allocate(
                                    levels,
                                    vehicle);

                    String ticketId =
                            "T-" +
                            ticketSequence
                                    .incrementAndGet();

                    Ticket ticket =
                            new Ticket(
                                    ticketId,
                                    vehicle,
                                    slot,
                                    LocalDateTime.now()
                            );

                    result[0] = ticket;

                    return ticket;
                }
        );

        return result[0];
    }

    public Ticket getActiveTicket(
            String licensePlate) {

        if (licensePlate == null) {
            return null;
        }

        return activeTickets.get(
                licensePlate.toUpperCase());
    }

    /*
     * Remove only if the map still contains
     * exactly this ticket.
     */
    public boolean removeActiveTicket(
            Ticket ticket) {

        Objects.requireNonNull(ticket);

        String licensePlate =
                ticket.getVehicle()
                        .getLicensePlate();

        return activeTickets.remove(
                licensePlate,
                ticket);
    }

    public List<Level> getLevels() {
        return levels;
    }

    public List<EntryGate> getEntryGates() {

        synchronized (entryGates) {

            return Collections.unmodifiableList(
                    new ArrayList<>(entryGates));
        }
    }

    public List<ExitGate> getExitGates() {

        synchronized (exitGates) {

            return Collections.unmodifiableList(
                    new ArrayList<>(exitGates));
        }
    }
}


/*
 * ============================================================
 * ENTRY GATE
 * ============================================================
 */

class EntryGate {

    private final int gateId;
    private final ParkingLot parkingLot;

    public EntryGate(
            int gateId,
            ParkingLot parkingLot) {

        if (gateId <= 0) {
            throw new IllegalArgumentException(
                    "Gate ID must be positive");
        }

        this.gateId = gateId;

        this.parkingLot =
                Objects.requireNonNull(
                        parkingLot);
    }

    public Ticket generateTicket(
            Vehicle vehicle) {

        Ticket ticket =
                parkingLot.parkVehicle(vehicle);

        System.out.printf(
                "ENTRY | SUCCESS | Gate: %d | " +
                "Ticket: %s | Vehicle: %s | " +
                "Floor: %d | Slot: %s%n",

                gateId,
                ticket.getTicketId(),
                vehicle,
                ticket.getSlot().getFloorNumber(),
                ticket.getSlot().getSlotId()
        );

        return ticket;
    }
}


/*
 * ============================================================
 * EXIT GATE
 * ============================================================
 */

class ExitGate {

    private final int gateId;
    private final ParkingLot parkingLot;

    private final FeeCalculationStrategy
            feeCalculationStrategy;

    /*
     * All ExitGates must use the same
     * PaymentService instance.
     */
    private final PaymentService paymentService;

    public ExitGate(
            int gateId,
            ParkingLot parkingLot,
            FeeCalculationStrategy feeCalculationStrategy,
            PaymentService paymentService) {

        if (gateId <= 0) {
            throw new IllegalArgumentException(
                    "Gate ID must be positive");
        }

        this.gateId = gateId;

        this.parkingLot =
                Objects.requireNonNull(
                        parkingLot);

        this.feeCalculationStrategy =
                Objects.requireNonNull(
                        feeCalculationStrategy);

        this.paymentService =
                Objects.requireNonNull(
                        paymentService);
    }

    public void processExit(
            Ticket ticket) {

        Objects.requireNonNull(ticket);

        /*
         * Ticket-level lock.
         *
         * Two exit gates cannot process
         * the same ticket simultaneously.
         */
        synchronized (ticket) {

            TicketStatus status =
                    ticket.getStatus();

            /*
             * PAYMENT_SUCCESS means payment already
             * happened but exit completion did not.
             *
             * Recovery scenario.
             */
            if (status ==
                    TicketStatus.PAYMENT_SUCCESS) {

                completePaidExit(ticket);
                return;
            }

            /*
             * Already paid.
             */
            if (status ==
                    TicketStatus.PAID) {

                System.out.printf(
                        "EXIT | IDEMPOTENT RETRY | " +
                        "Gate: %d | Ticket: %s | " +
                        "Already PAID | No charge%n",
                        gateId,
                        ticket.getTicketId());

                return;
            }

            /*
             * Cancelled ticket.
             */
            if (status ==
                    TicketStatus.CANCELLED) {

                throw new IllegalStateException(
                        "Cancelled ticket cannot exit: "
                                + ticket.getTicketId());
            }

            /*
             * Only ACTIVE ticket can start payment.
             */
            if (status !=
                    TicketStatus.ACTIVE) {

                throw new IllegalStateException(
                        "Invalid ticket state: "
                                + status);
            }

            /*
             * 1. Calculate fee
             */
            BigDecimal fee =
                    feeCalculationStrategy
                            .calculateFee(ticket);

            validateFee(fee);

            /*
             * 2. Stable idempotency key.
             *
             * One logical exit operation gets
             * one stable key.
             */
            String idempotencyKey =
                    "EXIT-" +
                    ticket.getTicketId();

            /*
             * 3. Process payment.
             */
            PaymentResult paymentResult =
                    paymentService.pay(
                            ticket.getTicketId(),
                            idempotencyKey,
                            fee);

            if (paymentResult.getStatus()
                    != PaymentStatus.SUCCESS) {

                throw new IllegalStateException(
                        "Payment failed for ticket "
                                + ticket.getTicketId());
            }

            /*
             * 4. Mark payment success BEFORE
             * releasing slot.
             *
             * If release fails, retry begins
             * from PAYMENT_SUCCESS.
             */
            ticket.markPaymentSuccess(
                    paymentResult);

            /*
             * 5. Complete exit.
             */
            completePaidExit(ticket);
        }
    }

    private void completePaidExit(
            Ticket ticket) {

        /*
         * Release parking slot.
         */
        releaseSlot(ticket);

        /*
         * Mark ticket as completely paid.
         */
        ticket.markPaid();

        /*
         * Remove ONLY this ticket from
         * active map.
         */
        boolean removed =
                parkingLot.removeActiveTicket(
                        ticket);

        if (!removed) {

            /*
             * Cleanup may already have happened.
             *
             * Do not rollback payment.
             */
            System.out.printf(
                    "EXIT | CLEANUP ALREADY DONE | " +
                    "Ticket: %s%n",
                    ticket.getTicketId());
        }

        PaymentResult payment =
                ticket.getPaymentResult();

        System.out.printf(
                "EXIT | SUCCESS | Gate: %d | " +
                "Ticket: %s | Amount: INR %.2f | " +
                "Transaction: %s | " +
                "Slot released: Floor %d, %s%n",

                gateId,
                ticket.getTicketId(),
                payment.getAmount(),
                payment.getTransactionId(),
                ticket.getSlot().getFloorNumber(),
                ticket.getSlot().getSlotId()
        );
    }

    private void releaseSlot(
            Ticket ticket) {

        boolean released =
                ticket.getSlot()
                        .release(
                                ticket.getVehicle());

        if (!released) {

            throw new IllegalStateException(
                    "Unable to release slot "
                            + ticket.getSlot().getSlotId()
                            + " for ticket "
                            + ticket.getTicketId());
        }
    }

    private void validateFee(
            BigDecimal fee) {

        if (fee == null ||
                fee.compareTo(
                        BigDecimal.ZERO) <= 0) {

            throw new IllegalStateException(
                    "Invalid parking fee");
        }
    }
}


/*
 * ============================================================
 * DEMO
 * ============================================================
 */

public class ParkingLotSolution {

    public static void main(
            String[] args) {

        /*
         * ======================================================
         * CREATE SLOTS
         * ======================================================
         */

        Slot slot1 =
                new Slot(
                        "L1-S1",
                        1,
                        SlotType.SMALL);

        Slot slot2 =
                new Slot(
                        "L1-S2",
                        1,
                        SlotType.MEDIUM);

        Slot slot3 =
                new Slot(
                        "L1-S3",
                        1,
                        SlotType.LARGE);

        Slot slot4 =
                new Slot(
                        "L2-S1",
                        2,
                        SlotType.SMALL);

        Slot slot5 =
                new Slot(
                        "L2-S2",
                        2,
                        SlotType.MEDIUM);

        Slot slot6 =
                new Slot(
                        "L2-S3",
                        2,
                        SlotType.LARGE);

        /*
         * ======================================================
         * CREATE LEVELS
         * ======================================================
         */

        Level level1 =
                new Level(
                        1,
                        Arrays.asList(
                                slot1,
                                slot2,
                                slot3
                        ));

        Level level2 =
                new Level(
                        2,
                        Arrays.asList(
                                slot4,
                                slot5,
                                slot6
                        ));

        List<Level> levels =
                Arrays.asList(
                        level1,
                        level2
                );

        /*
         * ======================================================
         * ALLOCATION STRATEGY
         * ======================================================
         */

        SlotAllocationStrategy allocationStrategy =
                new BestFitAllocationStrategy();

        /*
         * ======================================================
         * CREATE PARKING LOT
         * ======================================================
         */

        ParkingLot parkingLot =
                new ParkingLot(
                        levels,
                        allocationStrategy);

        /*
         * ======================================================
         * ONE SHARED PAYMENT SERVICE
         * ======================================================
         */

        PaymentService paymentService =
                new MockPaymentService();

        /*
         * ======================================================
         * FEE STRATEGIES
         * ======================================================
         */

        FeeCalculationStrategy hourlyFeeStrategy =
                new HourlyFeeCalculationStrategy(
                        new BigDecimal("50.00"));

        FeeCalculationStrategy fixedFeeStrategy =
                new FixedFeeCalculationStrategy(
                        new BigDecimal("100.00"));

        /*
         * ======================================================
         * CREATE EXIT GATES
         * ======================================================
         */

        ExitGate exitGate1 =
                new ExitGate(
                        1,
                        parkingLot,
                        hourlyFeeStrategy,
                        paymentService);

        ExitGate exitGate2 =
                new ExitGate(
                        2,
                        parkingLot,
                        fixedFeeStrategy,
                        paymentService);

        parkingLot.addExitGate(exitGate1);
        parkingLot.addExitGate(exitGate2);

        /*
         * ======================================================
         * CREATE ENTRY GATES
         * ======================================================
         */

        EntryGate entryGate1 =
                new EntryGate(
                        1,
                        parkingLot);

        EntryGate entryGate2 =
                new EntryGate(
                        2,
                        parkingLot);

        parkingLot.addEntryGate(entryGate1);
        parkingLot.addEntryGate(entryGate2);

        /*
         * ======================================================
         * CREATE VEHICLES
         * ======================================================
         */

        Vehicle car =
                new Vehicle(
                        "DL01AB1234",
                        VehicleType.MEDIUM);

        Vehicle bike =
                new Vehicle(
                        "DL01XY5678",
                        VehicleType.SMALL);

        Vehicle truck =
                new Vehicle(
                        "DL02TR3456",
                        VehicleType.LARGE);

        Vehicle secondBike =
                new Vehicle(
                        "DL02XY7890",
                        VehicleType.SMALL);

        Vehicle mediumVehicle =
                new Vehicle(
                        "DL03MD4567",
                        VehicleType.MEDIUM);

        Vehicle largeVehicle =
                new Vehicle(
                        "DL04LG8901",
                        VehicleType.LARGE);

        Vehicle waitingVehicle =
                new Vehicle(
                        "DL05WT2345",
                        VehicleType.SMALL);

        /*
         * ======================================================
         * PARK VEHICLES
         * ======================================================
         */

        Ticket carTicket =
                entryGate1.generateTicket(car);

        Ticket bikeTicket =
                entryGate2.generateTicket(bike);

        Ticket truckTicket =
                entryGate1.generateTicket(truck);

        Ticket secondBikeTicket =
                entryGate2.generateTicket(secondBike);

        Ticket mediumTicket =
                entryGate1.generateTicket(
                        mediumVehicle);

        Ticket largeTicket =
                entryGate2.generateTicket(
                        largeVehicle);

        /*
         * Parking lot is full.
         */
        try {

            entryGate1.generateTicket(
                    waitingVehicle);

        } catch (IllegalStateException e) {

            System.out.printf(
                    "ENTRY | REJECTED | " +
                    "Vehicle: %s | Reason: %s%n",
                    waitingVehicle.getLicensePlate(),
                    e.getMessage());
        }

        /*
         * ======================================================
         * EXIT
         * ======================================================
         */

        exitGate1.processExit(carTicket);

        exitGate2.processExit(bikeTicket);

        exitGate1.processExit(truckTicket);

        exitGate2.processExit(secondBikeTicket);

        exitGate1.processExit(mediumTicket);

        exitGate2.processExit(largeTicket);

        /*
         * ======================================================
         * IDEMPOTENT EXIT RETRY
         * ======================================================
         */

        System.out.println();
        System.out.println(
                "===== IDEMPOTENT EXIT RETRY =====");

        exitGate2.processExit(carTicket);

        /*
         * ======================================================
         * PAYMENT IDEMPOTENCY TEST
         * ======================================================
         */

        System.out.println();
        System.out.println(
                "===== PAYMENT IDEMPOTENCY TEST =====");

        String idempotencyKey =
                "TEST-PAYMENT-001";

        paymentService.pay(
                "TEST-TICKET",
                idempotencyKey,
                new BigDecimal("100.00"));

        /*
         * Same key + same amount.
         *
         * Existing payment returned.
         */
        paymentService.pay(
                "TEST-TICKET",
                idempotencyKey,
                new BigDecimal("100.00"));

        /*
         * Same key + DIFFERENT amount.
         *
         * Must fail.
         */
        try {

            paymentService.pay(
                    "TEST-TICKET",
                    idempotencyKey,
                    new BigDecimal("200.00"));

        } catch (IllegalStateException e) {

            System.out.println(
                    "PAYMENT | REJECTED | "
                            + e.getMessage());
        }

        /*
         * ======================================================
         * SAME TICKET + DIFFERENT IDEMPOTENCY KEY
         * ======================================================
         */

        PaymentResult retry =
                paymentService.pay(
                        "TEST-TICKET",
                        "ANOTHER-KEY",
                        new BigDecimal("100.00"));

        System.out.println(
                "PAYMENT | SAME TICKET RETRY | " +
                "Transaction: "
                        + retry.getTransactionId());

        /*
         * ======================================================
         * DUPLICATE VEHICLE TEST
         * ======================================================
         */

        System.out.println();
        System.out.println(
                "===== DUPLICATE VEHICLE TEST =====");

        Vehicle duplicateVehicle =
                new Vehicle(
                        "DL09DUP1234",
                        VehicleType.SMALL);

        Ticket duplicateTicket =
                entryGate1.generateTicket(
                        duplicateVehicle);

        try {

            entryGate2.generateTicket(
                    duplicateVehicle);

        } catch (IllegalStateException e) {

            System.out.println(
                    "ENTRY | DUPLICATE REJECTED | "
                            + e.getMessage());
        }

        /*
         * Exit duplicate vehicle.
         */
        exitGate1.processExit(
                duplicateTicket);

        /*
         * ======================================================
         * FINAL STATE
         * ======================================================
         */

        System.out.println();
        System.out.println(
                "===== FINAL SESSION SUMMARY =====");

        printTicketState(
                carTicket,
                slot2);

        printTicketState(
                bikeTicket,
                slot1);

        printTicketState(
                truckTicket,
                slot3);

        printTicketState(
                secondBikeTicket,
                slot4);

        printTicketState(
                mediumTicket,
                slot5);

        printTicketState(
                largeTicket,
                slot6);
    }

    private static void printTicketState(
            Ticket ticket,
            Slot slot) {

        System.out.printf(
                "Ticket: %s | " +
                "Vehicle: %s | " +
                "Status: %s | " +
                "Slot: %s | " +
                "Slot State: %s%n",

                ticket.getTicketId(),
                ticket.getVehicle()
                        .getLicensePlate(),
                ticket.getStatus(),
                slot.getSlotId(),
                slot.isFree()
                        ? "AVAILABLE"
                        : "OCCUPIED"
        );
    }
}