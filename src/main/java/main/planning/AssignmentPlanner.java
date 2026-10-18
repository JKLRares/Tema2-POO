package main.planning;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import main.Database;
import main.Milestone;
import main.enums.Priority;
import main.enums.Status;
import main.tickets.Ticket;
import main.users.DeveloperUser;
import main.users.SecurityManager;

/** Read-only, deterministic maximum-cardinality matching with developer capacities. */
public final class AssignmentPlanner {
    public record Assignment(int ticketID, String developer, Priority priority) { }
    public record Unplanned(int ticketID, String reason, List<String> eligibleDevelopers) {
        public Unplanned {
            eligibleDevelopers = List.copyOf(eligibleDevelopers);
        }
    }

    public record Plan(String milestone, boolean blocked, List<Assignment> assignments,
                       List<Unplanned> unplanned) {
        public Plan {
            assignments = List.copyOf(assignments);
            unplanned = List.copyOf(unplanned);
        }
    }

    /** Counts IN_PROGRESS and RESOLVED work across the entire session against capacity. */
    public Plan plan(final Database db, final Milestone milestone, final LocalDate date,
                     final int capacityPerDeveloper) {
        if (capacityPerDeveloper < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }
        List<Ticket> candidates = milestone.getTickets().stream()
                .filter(t -> t.getStatus() == Status.OPEN && t.getAssignedTo().isEmpty())
                .sorted(Comparator.comparing((Ticket t) -> milestone.priorityAt(t, date)).reversed()
                        .thenComparing(Ticket::getCreatedAt).thenComparing(Ticket::getId)).toList();
        List<DeveloperUser> developers = milestone.getAssignedDevs().stream().sorted()
                .map(db::getUser).map(user -> user.asDeveloper()).toList();
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (DeveloperUser developer : developers) {
            long active = db.getTickets().stream()
                    .filter(t -> developer.getUsername().equals(t.getAssignedTo()))
                    .filter(t -> t.getStatus() == Status.IN_PROGRESS
                            || t.getStatus() == Status.RESOLVED)
                    .count();
            remaining.put(developer.getUsername(),
                    (int) Math.max(0L, capacityPerDeveloper - active));
        }
        Map<Ticket, List<String>> eligible = new LinkedHashMap<>();
        Map<Ticket, Priority> priorities = new HashMap<>();
        for (Ticket ticket : candidates) {
            Priority priority = milestone.priorityAt(ticket, date);
            priorities.put(ticket, priority);
            eligible.put(ticket, developers.stream()
                    .filter(d -> SecurityManager.hasAccess(d, ticket, priority))
                    .map(DeveloperUser::getUsername).toList());
        }
        Map<Ticket, String> assignments = new HashMap<>();
        Map<String, List<Ticket>> workloads = new LinkedHashMap<>();
        remaining.keySet().forEach(name -> workloads.put(name, new ArrayList<>()));
        boolean blocked = milestone.isBlocked();
        boolean notStarted = date.isBefore(milestone.getStartDate());
        if (!blocked && !notStarted) {
            for (Ticket candidate : candidates) {
                augment(candidate, eligible, remaining, assignments, workloads);
            }
        }
        List<Assignment> planned = new ArrayList<>();
        List<Unplanned> unplanned = new ArrayList<>();
        for (Ticket ticket : candidates) {
            if (assignments.containsKey(ticket)) {
                planned.add(new Assignment(ticket.getId(), assignments.get(ticket),
                        priorities.get(ticket)));
            } else {
                String reason = blocked ? "BLOCKED" : notStarted ? "NOT_STARTED"
                        : eligible.get(ticket).isEmpty() ? "NO_ELIGIBLE_DEVELOPER" : "CAPACITY";
                unplanned.add(new Unplanned(ticket.getId(), reason, eligible.get(ticket)));
            }
        }
        return new Plan(milestone.getName(), blocked, planned, unplanned);
    }

    /** BFS finds an augmenting path; reversing it preserves every earlier matched ticket. */
    private void augment(final Ticket candidate, final Map<Ticket, List<String>> eligible,
                         final Map<String, Integer> remaining,
                         final Map<Ticket, String> assignments,
                         final Map<String, List<Ticket>> workloads) {
        ArrayDeque<Ticket> queue = new ArrayDeque<>();
        queue.add(candidate);
        Map<String, Ticket> reachedFrom = new HashMap<>();
        Set<Ticket> visited = new HashSet<>();
        visited.add(candidate);
        while (!queue.isEmpty()) {
            Ticket ticket = queue.remove();
            for (String developer : eligible.get(ticket)) {
                if (reachedFrom.putIfAbsent(developer, ticket) != null) {
                    continue;
                }
                List<Ticket> workload = workloads.get(developer);
                if (workload.size() < remaining.get(developer)) {
                    reversePath(developer, reachedFrom, assignments, workloads);
                    return;
                }
                for (Ticket occupant : workload) {
                    if (visited.add(occupant)) {
                        queue.add(occupant);
                    }
                }
            }
        }
    }

    private void reversePath(final String freeDeveloper, final Map<String, Ticket> reachedFrom,
                             final Map<Ticket, String> assignments,
                             final Map<String, List<Ticket>> workloads) {
        String developer = freeDeveloper;
        while (developer != null) {
            Ticket ticket = reachedFrom.get(developer);
            String former = assignments.put(ticket, developer);
            if (former != null) {
                workloads.get(former).remove(ticket);
            }
            workloads.get(developer).add(ticket);
            developer = former;
        }
    }
}
