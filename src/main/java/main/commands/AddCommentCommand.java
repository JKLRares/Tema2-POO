package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Comment;
import main.Database;
import main.tickets.Ticket;
import main.enums.Status;
import main.enums.UserRole;
import main.users.User;

import java.util.List;

public final class AddCommentCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    protected static final int MIN_COMMENT_LEN = 10;

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.getParams().get("ticketID");
        String commentContent = (String) input.getParams().get("comment");
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

        if (commentContent == null || commentContent.length() < MIN_COMMENT_LEN) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Comment must be at least 10 characters long.");
            outputs.add(error);
            return;
        }

        UserRole role = user.getRole();

        if (role == UserRole.DEVELOPER) {
            if (ticket.getAssignedTo() == null || !ticket.getAssignedTo().equals(username)) {
                ObjectNode error = mapper.createObjectNode();
                error.put("command", input.getCommand());
                error.put("username", username);
                error.put("timestamp", input.getTimestamp());
                error.put("error", "Ticket " + ticketId + " is not assigned to "
                        + "the developer " + username + ".");
                outputs.add(error);
                return;
            }
        } else if (role == UserRole.REPORTER) {
            if (ticket.getStatus() == Status.CLOSED) {
                ObjectNode error = mapper.createObjectNode();
                error.put("command", input.getCommand());
                error.put("username", username);
                error.put("timestamp", input.getTimestamp());
                error.put("error", "Reporters cannot comment on CLOSED tickets.");
                outputs.add(error);
                return;
            }

            if (!ticket.getReportedBy().equals(username)) {
                ObjectNode error = mapper.createObjectNode();
                error.put("command", input.getCommand());
                error.put("username", username);
                error.put("timestamp", input.getTimestamp());
                error.put("error", "Reporter " + username + " cannot "
                        + "comment on ticket " + ticketId + ".");
                outputs.add(error);
                return;
            }
        } else {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + username + " does not have "
                    + "permission to execute this command: required role REPORTER or DEVELOPER; "
                    + "user role " + role + ".");
            outputs.add(error);
            return;
        }

        Comment comment = new Comment(username, commentContent, input.getTimestamp());
        ticket.addComment(comment);
    }
}
