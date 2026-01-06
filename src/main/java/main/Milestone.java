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
            System.out.println("[" + name + "] is BLOCKED. Priorities frozen.");
            return;
        }

        System.out.println("[" + name + "] is ACTIVE. Checking rules...");

        long daysUntilDeadline = ChronoUnit.DAYS.between(currentDate, dueDate);
        boolean isLate = currentDate.isAfter(dueDate);

        if (isLate || daysUntilDeadline <= 1) {
            System.out.println("  -> CRISIS MODE (1 day before or late). Setting all to CRITICAL.");
            setAllTicketsToCritical();
        }
        else {
            long daysPassed = ChronoUnit.DAYS.between(startDate, currentDate);

            if (daysPassed < 0) daysPassed = 0;

            int stepsToIncrease = (int) (daysPassed / 3);

            System.out.println("  -> NORMAL MODE. Days passed: " + daysPassed + ". Increasing priorities by " + stepsToIncrease + " steps.");
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