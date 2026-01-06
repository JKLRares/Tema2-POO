package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class UndoAddCommentCommand implements Command {

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
            return;
        }

        if (ticket.getReportedBy() == null || ticket.getReportedBy().trim().isEmpty()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Comments are not allowed on anonymous tickets.");
            outputs.add(error);
            return;
        }

        List<Object> comments = ticket.getComments();
        Object commentToRemove = null;

        for (int i = comments.size() - 1; i >= 0; i--) {
            Object obj = comments.get(i);
            if (obj instanceof Comment) {
                Comment c = (Comment) obj;
                if (c.getAuthor().equals(username)) {
                    commentToRemove = obj;
                    break;
                }
            }
        }

        if (commentToRemove == null) {
            return;
        }
        comments.remove(commentToRemove);
    }
}