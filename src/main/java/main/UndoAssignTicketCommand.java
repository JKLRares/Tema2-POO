package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

public class UndoAssignTicketCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.params.get("ticketID");
        String username = input.username;

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

        if (ticket.getStatus() != Status.IN_PROGRESS) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The ticket " + ticketId + " is not in progress.");
            outputs.add(error);
            return;
        }

        String assignedTo = ticket.getAssignedTo();
        if (assignedTo == null || !assignedTo.equals(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The ticket " + ticketId + " is not assigned to user " + username + ".");
            outputs.add(error);
            return;
        }

        TicketHistoryEntry entry = new TicketHistoryEntry(
                HistoryActionType.DE_ASSIGNED,
                input.timestamp,
                username,
                "De-assigned from user: " + username
        );
        ticket.addHistoryEntry(entry);

        ticket.setAssignedTo("");
        ticket.setStatus(Status.OPEN);
        ticket.setAssignedAt("");
    }
}