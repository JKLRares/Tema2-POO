package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.enums.HistoryActionType;
import main.enums.Status;
import main.notifications.NotificationService;
import main.tickets.Ticket;
import main.tickets.TicketHistoryEntry;
import main.users.User;
import main.Milestone;

import java.time.LocalDate;
import java.util.List;

public final class ChangeStatusCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.getParams().get("ticketID");
        String username = input.getUsername();

        User user = db.getUser(username);
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + username + " does not exist.");
            outputs.add(error);
            return;
        }

        Ticket ticket = null;
        for (Ticket t : db.getTickets()) {
            if (t.getId().equals(ticketId)) {
                ticket = t;
                break;
            }
        }

        if (ticket == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Ticket " + ticketId + " does not exist.");
            outputs.add(error);
            return;
        }

        String assignedTo = ticket.getAssignedTo();
        if (assignedTo == null || !assignedTo.equals(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Ticket " + ticketId
                    + " is not assigned to developer " + username + ".");
            outputs.add(error);
            return;
        }

        Status currentStatus = ticket.getStatus();

        if (currentStatus == Status.CLOSED) {
            return;
        }

        Status newStatus = null;

        if (currentStatus == Status.IN_PROGRESS) {
            ticket.setStatus(Status.RESOLVED);
            ticket.setSolvedAt(input.getTimestamp());
            newStatus = Status.RESOLVED;
        } else if (currentStatus == Status.RESOLVED) {
            ticket.setStatus(Status.CLOSED);
            ticket.setSolvedAt(input.getTimestamp());
            newStatus = Status.CLOSED;
        }

        if (newStatus != null) {
            TicketHistoryEntry entry = new TicketHistoryEntry();
            entry.setAction(HistoryActionType.STATUS_CHANGED);
            entry.setTimestamp(input.getTimestamp());
            entry.setUser(username);
            entry.setFromStatus(currentStatus);
            entry.setToStatus(newStatus);
            ticket.addHistoryEntry(entry);
            NotificationService.getInstance().notify(ticket, "STATUS_CHANGED", username);

            Milestone milestone = db.getMilestoneForTicket(ticket.getId());
            if (milestone != null) {
                milestone.updateStatusAndPriorities(LocalDate.parse(input.getTimestamp()));

                // After closing a ticket, check if this action unblocks any dependent milestones
                if (milestone.areTicketsResolved()) {
                    for (Milestone m : db.getMilestones()) {
                        if (m.getDependencies().contains(milestone)) {
                            if (!m.isBlocked()) {
                                LocalDate currentDate = LocalDate.parse(input.getTimestamp());
                                boolean isLate = currentDate.isAfter(m.getDueDate());

                                if (!isLate) {
                                    String msg = "Milestone " + m.getName()
                                            + " is now unblocked as ticket " + ticket.getId()
                                            + " has been CLOSED.";
                                    if (m.getAssignedDevs() != null) {
                                        for (String dev : m.getAssignedDevs()) {
                                            /* Notify developers in the dependent milestone
                                            that they can now start working
                                             */
                                            NotificationService.getInstance()
                                                    .sendNotification(dev, msg);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
