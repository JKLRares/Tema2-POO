package main.users;

import main.tickets.Ticket;
import main.enums.Expertise;
import main.enums.Priority;
import main.enums.TicketType;

public final class SecurityManager {
    private static SecurityManager instance;

    private SecurityManager() { }

    /**
     * Gets the singleton instance.
     * @return instance
     */
    public static SecurityManager getInstance() {
        if (instance == null) {
            instance = new SecurityManager();
        }
        return instance;
    }

    /**
     * Checks if the user has access to the ticket.
     * @param user the user
     * @param ticket the ticket
     * @return true if access allowed
     */
    public boolean hasAccess(final User user, final Ticket ticket) {
        DeveloperUser dev = user.asDeveloper();
        if (dev == null) {
            System.out.println("Access Denied: Only Developers can access this workflow.");
            return false;
        }

        TicketType type = ticket.getType();
        Priority priority = ticket.getPriority();
        Expertise ticketCategory = ticket.getCategory();

        boolean seniorityCheckPassed = false;

        switch (dev.getSeniority()) {
            case JUNIOR:
                boolean isPriorityOkJr = (priority == Priority.LOW || priority == Priority.MEDIUM);
                boolean isTypeOkJr = (type == TicketType.BUG || type == TicketType.UI_FEEDBACK);

                if (isPriorityOkJr && isTypeOkJr) {
                    seniorityCheckPassed = true;
                }
                break;

            case MID:
                boolean isPriorityOkMid = (priority != Priority.CRITICAL);
                boolean isTypeOkMid = (type == TicketType.BUG || type == TicketType.UI_FEEDBACK
                        || type == TicketType.FEATURE_REQUEST);

                if (isPriorityOkMid && isTypeOkMid) {
                    seniorityCheckPassed = true;
                }
                break;

            case SENIOR:
                seniorityCheckPassed = true;
                break;
            default:
                break;
        }

        if (!seniorityCheckPassed) {
            return false;
        }
        Expertise devExpertise = dev.getExpertise();

        switch (devExpertise) {
            case FULLSTACK:
                return (ticketCategory == Expertise.FRONTEND
                        || ticketCategory == Expertise.BACKEND
                        || ticketCategory == Expertise.DEVOPS
                        || ticketCategory == Expertise.DESIGN
                        || ticketCategory == Expertise.DB);

            case FRONTEND:
                return (ticketCategory == Expertise.FRONTEND || ticketCategory == Expertise.DESIGN);

            case BACKEND:
                return (ticketCategory == Expertise.BACKEND || ticketCategory == Expertise.DB);

            case DEVOPS:
                return (ticketCategory == Expertise.DEVOPS);

            case DESIGN:
                return (ticketCategory == Expertise.DESIGN || ticketCategory == Expertise.FRONTEND);

            case DB:
                return (ticketCategory == Expertise.DB);

            default:
                return false;
        }
    }
}
