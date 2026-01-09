package main;

public class NotificationService {
    private static NotificationService instance;

    private NotificationService() {}

    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    public void sendNotification(String username, String message) {
        Database db = Database.getInstance();
        User user = db.getUser(username);
        if (user != null) {
            user.update(message);
        }
    }

    public void notify(Ticket ticket, String action, String actorUsername) {
        Database db = Database.getInstance();

        String message = generateMessage(ticket.getId(), null, action, actorUsername);

        String reporterName = ticket.getReportedBy();
        if (reporterName != null && !reporterName.isEmpty() && !reporterName.equals(actorUsername)) {
            User reporter = db.getUser(reporterName);
            if (reporter != null) {
                reporter.update(message);
            }
        }

        String assignedName = ticket.getAssignedTo();
        if (assignedName != null && !assignedName.isEmpty() && !assignedName.equals(actorUsername)) {
            User assignee = db.getUser(assignedName);
            if (assignee != null) {
                assignee.update(message);
            }
        }
    }

    public void notifyMilestone(Milestone milestone, String action, String actorUsername) {
        String message = "";

        if ("MILESTONE_CREATED".equals(action)) {
            message = "New milestone " + milestone.getName() + " has been created with due date " + milestone.getDueDate() + ".";
        }

        if (!message.isEmpty() && milestone.getAssignedDevs() != null) {
            for (String devName : milestone.getAssignedDevs()) {
                sendNotification(devName, message);
            }
        }
    }

    public void notifyMilestoneEvent(Milestone milestone, String eventType) {
        String message = "";

        if ("DUE_TOMORROW".equals(eventType)) {
            message = "Milestone " + milestone.getName() + " is due tomorrow. All unresolved tickets are now CRITICAL.";
        } else if ("UNBLOCKED_LATE".equals(eventType)) {
            message = "Milestone " + milestone.getName() + " was unblocked after due date. All active tickets are now CRITICAL.";
        }

        if (!message.isEmpty() && milestone.getAssignedDevs() != null) {
            for (String devName : milestone.getAssignedDevs()) {
                sendNotification(devName, message);
            }
        }
    }

    private String generateMessage(Integer ticketId, String milestoneName, String action, String actor) {
        switch (action) {
            case "NEW_COMMENT":
                return "Utilizatorul " + actor + " a adaugat un comentariu la tichetul " + ticketId + ".";
            case "STATUS_CHANGED":
                return "Statusul tichetului " + ticketId + " a fost schimbat de " + actor + ".";
            default:
                if (milestoneName != null) {
                    return "Eveniment milestone " + milestoneName + " generat de " + actor + ".";
                }
                return "Eveniment nou (" + action + ") generat de " + actor + ".";
        }
    }
}