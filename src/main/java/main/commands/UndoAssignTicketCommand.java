package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.tickets.TicketHistoryEntry;
import main.enums.HistoryActionType;
import main.enums.Status;

import java.util.List;

public class UndoAssignTicketCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.getParams().get("ticketID");
        String username = input.getUsername();

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

        if (ticket.getStatus() != Status.IN_PROGRESS) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The ticket " + ticketId + " is not in progress.");
            outputs.add(error);
            return;
        }

        String assignedTo = ticket.getAssignedTo();
        if (assignedTo == null || !assignedTo.equals(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The ticket " + ticketId + " is not assigned to user "
                    + username + ".");
            outputs.add(error);
            return;
        }

        TicketHistoryEntry entry = new TicketHistoryEntry(
                HistoryActionType.DE_ASSIGNED,
                input.getTimestamp(),
                username,
                "De-assigned from user: " + username
        );
        ticket.addHistoryEntry(entry);

        ticket.setAssignedTo("");
        ticket.setStatus(Status.OPEN);
        ticket.setAssignedAt("");
    }
}
