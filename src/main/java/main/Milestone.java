package main;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import main.enums.Priority;
import main.enums.Status;

import lombok.Getter;
import main.notifications.NotificationService;
import main.tickets.Ticket;

@Getter
public final class Milestone {
    private static final int PRIORITY_INCREASE_INTERVAL = 3;
    private final String name;
    private final LocalDate startDate;
    private final LocalDate dueDate;
    private List<Ticket> tickets;

    private List<Milestone> dependencies;
    private List<String> blockingFor;
    private List<String> assignedDevs;
    private String creator;
    private Map<Ticket, Priority> initialPriorities;
    private boolean notifiedDueTomorrow = false;
    private boolean wasBlocked = false;
    private LocalDate priorityEpochDate;
    private boolean membershipSealed;

    public Milestone(final String name, final LocalDate startDate, final LocalDate dueDate) {
        this.name = name;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.tickets = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.assignedDevs = new ArrayList<>();
        this.blockingFor = new ArrayList<>();
        this.initialPriorities = new HashMap<>();
        this.notifiedDueTomorrow = false;
        this.wasBlocked = false;
        this.priorityEpochDate = startDate;
    }

    /**
     * Adds a ticket to the milestone.
     * @param ticket the ticket to add
     */
    public void addTicket(final Ticket ticket) {
        if (membershipSealed || tickets.contains(ticket)) {
            throw new IllegalStateException("Milestone membership is already fixed or duplicated");
        }
        tickets.add(ticket);
        initialPriorities.put(ticket, ticket.getPriority());
    }

    /**
     * Adds a dependency milestone.
     * @param milestone the dependency
     */
    public void addDependency(final Milestone milestone) {
        dependencies.add(milestone);
        wasBlocked = isBlocked();
    }

    /**
     * Adds a developer to the milestone.
     * @param username the developer's username
     */
    public void addDeveloper(final String username) {
        if (membershipSealed || assignedDevs.contains(username)) {
            throw new IllegalStateException("Milestone developers are already fixed or duplicated");
        }
        assignedDevs.add(username);
    }

    void sealMembership() {
        membershipSealed = true;
    }

    public List<Ticket> getTickets() {
        return List.copyOf(tickets);
    }

    public List<Milestone> getDependencies() {
        return List.copyOf(dependencies);
    }

    public List<String> getAssignedDevs() {
        return List.copyOf(assignedDevs);
    }

    public List<String> getBlockingFor() {
        return List.copyOf(blockingFor);
    }

    public Map<Ticket, Priority> getInitialPriorities() {
        return Map.copyOf(initialPriorities);
    }

    /** Copies dependency names before membership is sealed. */
    public void setBlockingFor(final List<String> names) {
        if (membershipSealed) {
            throw new IllegalStateException("Milestone membership is fixed");
        }
        blockingFor = new ArrayList<>(names);
    }

    /** Sets ownership during creation; registered ownership cannot change. */
    public void setCreator(final String username) {
        if (membershipSealed) {
            throw new IllegalStateException("Milestone ownership is fixed");
        }
        creator = username;
    }

    private Priority calculateIncreasedPriority(final Priority startPriority, final long steps) {
        if (steps <= 0) {
            return startPriority;
        }

        int currentOrdinal = startPriority.ordinal();
        int maxOrdinal = Priority.CRITICAL.ordinal();
        long newOrdinal = currentOrdinal + steps;

        if (newOrdinal >= maxOrdinal) {
            return Priority.CRITICAL;
        }
        return Priority.values()[(int) newOrdinal];
    }

    /**
     * Checks if the milestone is blocked by dependencies.
     * @return true if blocked
     */
    public boolean isBlocked() {
        for (Milestone dep : dependencies) {
            if (!dep.areTicketsResolved()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if all tickets in the milestone are resolved.
     * @return true if all tickets are closed
     */
    public boolean areTicketsResolved() {
        for (Ticket t : tickets) {
            if (t.getStatus() != Status.CLOSED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Updates the status and priorities of tickets in the milestone based on the current date.
     * @param currentDate the current date
     */
    public void updateStatusAndPriorities(final LocalDate currentDate,
                                          final NotificationService notifications) {
        if (!tickets.isEmpty() && areTicketsResolved()) {
            return;
        }
        if (isBlocked()) {
            this.wasBlocked = true;
            if (currentDate.isAfter(priorityEpochDate)) {
                priorityEpochDate = currentDate;
            }
            return;
        }

        long daysUntilDeadline = ChronoUnit.DAYS.between(currentDate, dueDate);
        boolean isLate = currentDate.isAfter(dueDate);

        if (this.wasBlocked && isLate) {
            notifications.notifyMilestoneEvent(this, "UNBLOCKED_LATE");
            this.wasBlocked = false;
        } else {
            this.wasBlocked = false;
        }

        if (daysUntilDeadline <= 1 && !this.notifiedDueTomorrow && !isLate) {
            notifications.notifyMilestoneEvent(this, "DUE_TOMORROW");
            this.notifiedDueTomorrow = true;
        }

        // If the milestone has passed its due date all active tickets become CRITICAL
        if (isLate || daysUntilDeadline <= 1) {
            setAllTicketsToCritical();
        } else {
            long daysPassed = ChronoUnit.DAYS.between(priorityEpochDate, currentDate);

            if (daysPassed < 0) {
                daysPassed = 0;
            }

            // Gradually increase ticket priority for every 3 days passed since the milstone start
            long stepsToIncrease = daysPassed / PRIORITY_INCREASE_INTERVAL;

            applyProgressivePriority(stepsToIncrease);
        }
    }

    /**
     * Projects an open ticket's effective priority without changing state or sending
     * notifications.
     */
    public Priority priorityAt(final Ticket ticket, final LocalDate date) {
        if (ticket.getStatus() == Status.CLOSED || isBlocked()) {
            return ticket.getPriority();
        }
        if (ChronoUnit.DAYS.between(date, dueDate) <= 1) {
            return Priority.CRITICAL;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(priorityEpochDate, date));
        Priority initial = initialPriorities.getOrDefault(ticket, ticket.getPriority());
        Priority increased = calculateIncreasedPriority(initial, days / PRIORITY_INCREASE_INTERVAL);
        return increased.ordinal() > ticket.getPriority().ordinal()
                ? increased : ticket.getPriority();
    }

    private void setAllTicketsToCritical() {
        for (Ticket t : tickets) {
            if (t.getStatus() != Status.CLOSED) {
                t.increasePriority(Priority.CRITICAL);
            }
        }
    }

    private void applyProgressivePriority(final long steps) {
        for (Ticket t : tickets) {
            if (t.getStatus() != Status.CLOSED) {
                Priority initial = initialPriorities.get(t);
                if (initial == null) {
                    initial = t.getPriority();
                }

                Priority newPriority = calculateIncreasedPriority(initial, steps);
                if (newPriority.ordinal() > t.getPriority().ordinal()) {
                    t.increasePriority(newPriority);
                }
            }
        }
    }
}
