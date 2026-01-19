package main.notifications;

import main.Database;
import main.Milestone;
import main.tickets.Ticket;
import main.users.User;

public final class NotificationService {
    private static NotificationService instance;

    private NotificationService() {
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    /**
     * Sends notification.
     *
     * @param username the username
     * @param message  the message
     */
    public void sendNotification(final String username, final String message) {
        Database db = Database.getInstance();
        User user = db.getUser(username);
        if (user != null) {
            user.update(message);
        }
    }

    /**
     * Notifies about ticket.
     *
     * @param ticket        the ticket
     * @param action        the action
     * @param actorUsername the actor username
     */
    public void notify(final Ticket ticket, final String action, final String actorUsername) {
        Database db = Database.getInstance();

        String message = "";

        String reporterName = ticket.getReportedBy();
        if (reporterName != null && !reporterName.isEmpty()
                && !reporterName.equals(actorUsername)) {
            User reporter = db.getUser(reporterName);
            if (reporter != null) {
                reporter.update(message);
            }
        }

        String assignedName = ticket.getAssignedTo();
        if (assignedName != null && !assignedName.isEmpty()
                && !assignedName.equals(actorUsername)) {
            User assignee = db.getUser(assignedName);
            if (assignee != null) {
                assignee.update(message);
            }
        }
    }

    /**
     * Notifies about milestone.
     *
     * @param milestone     the milestone
     * @param action        the action
     * @param actorUsername the actor username
     */
    public void notifyMilestone(final Milestone milestone, final String action,
                                final String actorUsername) {
        String message = "";

        if ("MILESTONE_CREATED".equals(action)) {
            message = "New milestone " + milestone.getName()
                    + " has been created with due date " + milestone.getDueDate() + ".";
        }

        if (!message.isEmpty() && milestone.getAssignedDevs() != null) {
            for (String devName : milestone.getAssignedDevs()) {
                sendNotification(devName, message);
            }
        }
    }

    /**
     * Notifies about milestone event.
     *
     * @param milestone the milestone
     * @param eventType the event type
     */
    public void notifyMilestoneEvent(final Milestone milestone, final String eventType) {
        String message = "";

        if ("DUE_TOMORROW".equals(eventType)) {
            message = "Milestone " + milestone.getName()
                    + " is due tomorrow. All unresolved tickets are now CRITICAL.";
        } else if ("UNBLOCKED_LATE".equals(eventType)) {
            message = "Milestone " + milestone.getName()
                    + " was unblocked after due date. All active tickets are now CRITICAL.";
        }

        if (!message.isEmpty() && milestone.getAssignedDevs() != null) {
            for (String devName : milestone.getAssignedDevs()) {
                sendNotification(devName, message);
            }
        }
    }
}
