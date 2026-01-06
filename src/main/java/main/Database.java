package main;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class Database {
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

    public Milestone getMilestoneForTicket(Integer ticketId) {
        for (Milestone m : milestones) {
            for (Ticket t : m.getTickets()) {
                if (t.getId().equals(ticketId)) {
                    return m;
                }
            }
        }
        return null;
    }

    public void addMilestone(Milestone m) {
        this.milestones.add(m);
    }

    public static synchronized Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    public static void reset() {
        instance = new Database();
    }

    public void addUser(User user) {
        this.users.add(user);
    }

    public void addTicket(Ticket ticket) {
        this.tickets.add(ticket);
    }

    public User getUser(String username) {
        if (username == null) return null;
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