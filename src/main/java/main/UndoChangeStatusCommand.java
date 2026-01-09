package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class UndoChangeStatusCommand implements Command {

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

        if (currentStatus == Status.IN_PROGRESS) {
            return;
        }

        Status newStatus = null;

        if (currentStatus == Status.CLOSED) {
            ticket.setStatus(Status.RESOLVED);
            newStatus = Status.RESOLVED;
        } else if (currentStatus == Status.RESOLVED) {
            ticket.setStatus(Status.IN_PROGRESS);
            newStatus = Status.IN_PROGRESS;
        }

        if (newStatus != null) {
            TicketHistoryEntry entry = new TicketHistoryEntry(
                    HistoryActionType.STATUS_CHANGED,
                    input.timestamp,
                    username,
                    "Status reverted from " + currentStatus + " to " + newStatus
            );
            entry.setFromStatus(currentStatus);
            entry.setToStatus(newStatus);
            ticket.addHistoryEntry(entry);
        }
    }
}