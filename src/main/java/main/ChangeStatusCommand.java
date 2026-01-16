package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.List;

public class ChangeStatusCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.params.get("ticketID");
        String username = input.username;

        User user = db.getUser(username);
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
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
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Ticket " + ticketId + " does not exist.");
            outputs.add(error);
            return;
        }

        String assignedTo = ticket.getAssignedTo();
        if (assignedTo == null || !assignedTo.equals(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Ticket " + ticketId + " is not assigned to developer " + username + ".");
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
            ticket.setSolvedAt(input.timestamp);
            newStatus = Status.RESOLVED;
        } else if (currentStatus == Status.RESOLVED) {
            ticket.setStatus(Status.CLOSED);
            ticket.setSolvedAt(input.timestamp);
            newStatus = Status.CLOSED;
        }

        if (newStatus != null) {
            TicketHistoryEntry entry = new TicketHistoryEntry();
            entry.setAction(HistoryActionType.STATUS_CHANGED);
            entry.setTimestamp(input.timestamp);
            entry.setUser(username);
            entry.setFromStatus(currentStatus);
            entry.setToStatus(newStatus);
            ticket.addHistoryEntry(entry);
            NotificationService.getInstance().notify(ticket, "STATUS_CHANGED", username);

            Milestone milestone = db.getMilestoneForTicket(ticket.getId());
            if (milestone != null) {
                milestone.updateStatusAndPriorities(LocalDate.parse(input.timestamp));

                if (milestone.areTicketsResolved()) {
                    for (Milestone m : db.getMilestones()) {
                        if (m.getDependencies().contains(milestone)) {
                            if (!m.isBlocked()) {
                                LocalDate currentDate = LocalDate.parse(input.timestamp);
                                boolean isLate = currentDate.isAfter(m.getDueDate());

                                if (!isLate) {
                                    String msg = "Milestone " + m.getName() + " is now unblocked as ticket " + ticket.getId() + " has been CLOSED.";
                                    if (m.getAssignedDevs() != null) {
                                        for (String dev : m.getAssignedDevs()) {
                                            NotificationService.getInstance().sendNotification(dev, msg);
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