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
import lombok.Setter;
import main.notifications.NotificationService;
import main.tickets.Ticket;

@Getter @Setter
public final class Milestone {
    private static final int THREE = 3;

    private String name;
    private LocalDate startDate;
    private LocalDate dueDate;
    private List<Ticket> tickets;

    private List<Milestone> dependencies;
    private List<String> blockingFor;
    private List<String> assignedDevs;
    private String creator;
    private Map<Ticket, Priority> initialPriorities;

    private boolean isCompleted;
    private boolean notifiedDueTomorrow = false;
    private boolean wasBlocked = false;
    private LocalDate priorityEpochDate;

    public Milestone(final String name, final LocalDate startDate, final LocalDate dueDate) {
        this.name = name;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.tickets = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.assignedDevs = new ArrayList<>();
        this.blockingFor = new ArrayList<>();
        this.isCompleted = false;
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
        tickets.add(ticket);
        initialPriorities.put(ticket, ticket.getPriority());
    }

    /**
     * Adds a dependency milestone.
     * @param milestone the dependency
     */
    public void addDependency(final Milestone milestone) {
        dependencies.add(milestone);
    }

    /**
     * Adds a developer to the milestone.
     * @param username the developer's username
     */
    public void addDeveloper(final String username) {
        assignedDevs.add(username);
    }

    private Priority calculateIncreasedPriority(final Priority startPriority, final int steps) {
        if (steps <= 0) {
            return startPriority;
        }

        int currentOrdinal = startPriority.ordinal();
        int maxOrdinal = Priority.CRITICAL.ordinal();

        int newOrdinal = currentOrdinal + steps;

        if (newOrdinal >= maxOrdinal) {
            return Priority.CRITICAL;
        }

        return Priority.values()[newOrdinal];
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
    public void updateStatusAndPriorities(final LocalDate currentDate) {
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
            NotificationService.getInstance().notifyMilestoneEvent(this, "UNBLOCKED_LATE");
            this.wasBlocked = false;
        } else {
            this.wasBlocked = false;
        }

        if (daysUntilDeadline <= 1 && !this.notifiedDueTomorrow && !isLate) {
            NotificationService.getInstance().notifyMilestoneEvent(this, "DUE_TOMORROW");
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
            int stepsToIncrease = (int) (daysPassed / THREE);

            applyProgressivePriority(stepsToIncrease);
        }
    }

    private void setAllTicketsToCritical() {
        for (Ticket t : tickets) {
            if (t.getStatus() != Status.CLOSED) {
                t.setBusinessPriority(Priority.CRITICAL);
            }
        }
    }

    private void applyProgressivePriority(final int steps) {
        for (Ticket t : tickets) {
            if (t.getStatus() != Status.CLOSED) {
                Priority initial = initialPriorities.get(t);
                if (initial == null) {
                    initial = t.getPriority();
                }

                Priority newPriority = calculateIncreasedPriority(initial, steps);

                t.setBusinessPriority(newPriority);
            }
        }
    }
}
