package main;

import lombok.Getter;
import lombok.Setter;
import main.tickets.Ticket;
import main.users.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public final class Database {
    private static Database instance;

    private List<User> users;
    private List<Ticket> tickets;
    private int nextTicketId;
    private boolean testingPhase;
    private boolean active;
    private LocalDate testingPhaseStartDate;
    private List<Milestone> milestones = new ArrayList<>();

    private Database() {
        this.users = new ArrayList<>();
        this.tickets = new ArrayList<>();
        this.nextTicketId = 0;
        this.testingPhase = true;
        this.active = true;
        this.testingPhaseStartDate = null;
    }

    /**
     * Gets milestone for ticket.
     *
     * @param ticketId the ticket id
     * @return the milestone for ticket
     */
    public Milestone getMilestoneForTicket(final Integer ticketId) {
        for (Milestone m : milestones) {
            for (Ticket t : m.getTickets()) {
                if (t.getId().equals(ticketId)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Adds a milestone.
     * @param m the milestone
     */
    public void addMilestone(final Milestone m) {
        this.milestones.add(m);
    }

    /**
     * Gets the instance.
     * @return the instance
     */
    public static Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    /**
     * Resets the database.
     */
    public static void reset() {
        instance = new Database();
    }

    /**
     * Adds a user.
     * @param user the user
     */
    public void addUser(final User user) {
        this.users.add(user);
    }

    /**
     * Adds a ticket.
     * @param ticket the ticket
     */
    public void addTicket(final Ticket ticket) {
        this.tickets.add(ticket);
    }

    /**
     * Gets user.
     * @param username the username
     * @return the user
     */
    public User getUser(final String username) {
        if (username == null) {
            return null;
        }
        for (User u : users) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }

    public List<Ticket> getTickets() {
        return new ArrayList<>(this.tickets);
    }

    public int getNextIdAndIncrement() {
        return nextTicketId++;
    }
}
