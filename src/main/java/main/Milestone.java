package main;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
class Milestone {
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

    public Milestone(String name, LocalDate startDate, LocalDate dueDate) {
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

    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        initialPriorities.put(ticket, ticket.getPriority());
    }

    public void addDependency(Milestone milestone) {
        dependencies.add(milestone);
    }

    public void addDeveloper(String username) {
        assignedDevs.add(username);
    }

    private Priority calculateIncreasedPriority(Priority startPriority, int steps) {
        if (steps <= 0) return startPriority;

        int currentOrdinal = startPriority.ordinal();
        int maxOrdinal = Priority.CRITICAL.ordinal();

        int newOrdinal = currentOrdinal + steps;

        if (newOrdinal >= maxOrdinal) {
            return Priority.CRITICAL;
        }

        return Priority.values()[newOrdinal];
    }

    public boolean isBlocked() {
        for (Milestone dep : dependencies) {
            if (!dep.areTicketsResolved()) {
                return true;
            }
        }
        return false;
    }

    public boolean areTicketsResolved() {
        for (Ticket t : tickets) {
            if (t.status != Status.CLOSED) {
                return false;
            }
        }
        return true;
    }

    public void updateStatusAndPriorities(LocalDate currentDate) {
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

        if (isLate || daysUntilDeadline <= 1) {
            setAllTicketsToCritical();
        }
        else {
            long daysPassed = ChronoUnit.DAYS.between(priorityEpochDate, currentDate);

            if (daysPassed < 0) daysPassed = 0;

            int stepsToIncrease = (int) (daysPassed / 3);

            applyProgressivePriority(stepsToIncrease);
        }
    }

    private void setAllTicketsToCritical() {
        for (Ticket t : tickets) {
            if (t.status != Status.CLOSED) {
                t.businessPriority = Priority.CRITICAL;
            }
        }
    }

    private void applyProgressivePriority(int steps) {
        for (Ticket t : tickets) {
            if (t.status != Status.CLOSED) {
                Priority initial = initialPriorities.get(t);
                if (initial == null) initial = t.getPriority();

                Priority newPriority = calculateIncreasedPriority(initial, steps);

                t.businessPriority = newPriority;
            }
        }
    }
}