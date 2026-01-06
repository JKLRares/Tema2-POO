package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class AddCommentCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.params.get("ticketID");
        String commentContent = (String) input.params.get("comment");
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

        if (commentContent == null || commentContent.length() < 10) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Comment must be at least 10 characters long.");
            outputs.add(error);
            return;
        }

        UserRole role = user.getRole();

        if (role == UserRole.DEVELOPER) {
            if (ticket.getAssignedTo() == null || !ticket.getAssignedTo().equals(username)) {
                ObjectNode error = mapper.createObjectNode();
                error.put("command", input.command);
                error.put("username", username);
                error.put("timestamp", input.timestamp);
                error.put("error", "Ticket " + ticketId + " is not assigned to the developer " + username + ".");
                outputs.add(error);
                return;
            }
        } else if (role == UserRole.REPORTER) {
            if (ticket.getStatus() == Status.CLOSED) {
                ObjectNode error = mapper.createObjectNode();
                error.put("command", input.command);
                error.put("username", username);
                error.put("timestamp", input.timestamp);
                error.put("error", "Cannot add comments to closed tickets.");
                outputs.add(error);
                return;
            }

            if (!ticket.getReportedBy().equals(username)) {
                ObjectNode error = mapper.createObjectNode();
                error.put("command", input.command);
                error.put("username", username);
                error.put("timestamp", input.timestamp);
                error.put("error", "Reporter " + username + " cannot comment on ticket " + ticketId + ".");
                outputs.add(error);
                return;
            }
        } else {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + username + " does not have permission to execute this command: required role REPORTER or DEVELOPER; user role " + role + ".");
            outputs.add(error);
            return;
        }

        Comment comment = new Comment(username, commentContent, input.timestamp);
        ticket.getComments().add(comment);
    }
}
