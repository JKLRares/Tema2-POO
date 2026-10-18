package main;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import main.enums.HistoryActionType;
import main.enums.Status;
import main.notifications.NotificationService;
import main.tickets.Ticket;
import main.users.SecurityManager;
import main.users.User;

/** In-memory repository owned by one command session. Insertion order is deterministic. */
public final class Database {
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<Integer, Ticket> tickets = new LinkedHashMap<>();
    private final Map<String, Milestone> milestones = new LinkedHashMap<>();
    private final Map<Integer, Milestone> ticketMilestones = new LinkedHashMap<>();
    @Getter private final NotificationService notifications = new NotificationService(this);
    private long nextTicketId;
    @Getter @Setter private boolean testingPhase = true;
    @Getter @Setter private boolean active = true;
    @Getter @Setter private LocalDate testingPhaseStartDate;

    public Database() {
    }

    /** Returns the owning milestone, or null for a ticket outside any milestone. */
    public Milestone getMilestoneForTicket(final Integer ticketId) {
        return ticketMilestones.get(ticketId);
    }

    /** Looks up a milestone by its unique name; unknown names return null. */
    public Milestone getMilestone(final String name) {
        return milestones.get(name);
    }

    /** Looks up a ticket by ID; unknown IDs return null. */
    public Ticket getTicket(final Integer id) {
        return tickets.get(id);
    }

    /** Registers the whole aggregate only after checking every ticket membership. */
    public void addMilestone(final Milestone milestone) {
        Objects.requireNonNull(milestone);
        if (milestones.containsKey(milestone.getName())) {
            throw new IllegalArgumentException("Duplicate milestone: " + milestone.getName());
        }
        for (Ticket ticket : milestone.getTickets()) {
            if (tickets.get(ticket.getId()) != ticket
                    || ticketMilestones.containsKey(ticket.getId())) {
                throw new IllegalArgumentException("Invalid milestone ticket: " + ticket.getId());
            }
        }
        milestone.sealMembership();
        milestones.put(milestone.getName(), milestone);
        for (Ticket ticket : milestone.getTickets()) {
            ticketMilestones.put(ticket.getId(), milestone);
        }
    }

    /** Registers one unique username; duplicates never replace an existing user. */
    public void addUser(final User user) {
        Objects.requireNonNull(user);
        if (user.getUsername() == null || user.getUsername().isBlank()
                || users.containsKey(user.getUsername())) {
            throw new IllegalArgumentException("Missing or duplicate username");
        }
        users.put(user.getUsername(), user);
    }

    /** Registers a unique ID and advances the allocator only after validation. */
    public void addTicket(final Ticket ticket) {
        Objects.requireNonNull(ticket);
        if (ticket.getId() == null || ticket.getId() < 0 || tickets.containsKey(ticket.getId())) {
            throw new IllegalArgumentException("Missing, negative or duplicate ticket ID");
        }
        long next = Math.max(nextTicketId, (long) ticket.getId() + 1);
        tickets.put(ticket.getId(), ticket);
        nextTicketId = next;
    }

    /** Returns the user identified by username, or null when absent. */
    public User getUser(final String username) {
        return users.get(username);
    }

    public List<User> getUsers() {
        return List.copyOf(users.values());
    }

    public List<Ticket> getTickets() {
        return new ArrayList<>(tickets.values());
    }

    public List<Milestone> getMilestones() {
        return List.copyOf(milestones.values());
    }

    /** Keeps the exhausted allocator distinct from every valid nonnegative 32-bit ID. */
    public int getNextTicketId() {
        if (!hasAvailableTicketId()) {
            throw new IllegalStateException("Ticket ID space is exhausted");
        }
        return (int) nextTicketId;
    }

    public boolean hasAvailableTicketId() {
        return nextTicketId <= Integer.MAX_VALUE;
    }

    /** Applies the assignment rules again after a milestone changes priorities. */
    public void refreshMilestone(final Milestone milestone, final LocalDate date) {
        milestone.updateStatusAndPriorities(date, notifications);
        for (Ticket ticket : milestone.getTickets()) {
            User assignee = getUser(ticket.getAssignedTo());
            if (ticket.getStatus() != Status.CLOSED && assignee != null
                    && !SecurityManager.hasAccess(assignee, ticket)) {
                ticket.releaseAssignment(date.toString(), HistoryActionType.REMOVED_FROM_DEV);
            }
        }
    }

    /** Observes every milestone at the supplied simulated date. */
    public void refreshMilestones(final LocalDate date) {
        for (Milestone milestone : milestones.values()) {
            refreshMilestone(milestone, date);
        }
    }
}
