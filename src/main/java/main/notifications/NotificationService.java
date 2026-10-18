package main.notifications;

import main.Database;
import main.Milestone;
import main.users.User;

public final class NotificationService {
    private final Database db;
    public NotificationService(final Database database) {
        this.db = database;
    }

    /**
     * Sends notification.
     *
     * @param username the username
     * @param message  the message
     */
    public void sendNotification(final String username, final String message) {
        User user = db.getUser(username);
        if (user != null) {
            user.update(message);
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
