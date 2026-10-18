package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.enums.HistoryActionType;
import main.enums.Status;

import java.util.List;

public final class UndoAssignTicketCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.getParams().get("ticketID");
        String username = input.getUsername();
        Ticket ticket = db.getTicket(ticketId);

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
            error.put("error", "Only IN_PROGRESS tickets can be unassigned.");
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
        ticket.releaseAssignment(input.getTimestamp(), HistoryActionType.DE_ASSIGNED);
    }
}
