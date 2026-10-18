package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;

import java.util.List;

public final class UndoAddCommentCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db, final List<ObjectNode>
            outputs) {
        Integer ticketId = (Integer) input.getParams().get("ticketID");
        String username = input.getUsername();
        Ticket ticket = db.getTicket(ticketId);

        if (ticket == null) {
            return;
        }

        if (ticket.getReportedBy() == null || ticket.getReportedBy().trim().isEmpty()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Comments are not allowed on anonymous tickets.");
            outputs.add(error);
            return;
        }
        ticket.undoComment(username);
    }
}
