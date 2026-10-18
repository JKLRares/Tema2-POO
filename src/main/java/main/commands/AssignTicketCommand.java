package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.time.LocalDate;

import main.Database;
import main.Milestone;
import main.enums.Expertise;
import main.enums.Status;
import main.tickets.Ticket;
import main.users.DeveloperUser;
import main.users.SecurityManager;
import main.users.User;

public final class AssignTicketCommand implements Command {

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

        DeveloperUser dev = user.asDeveloper();
        if (dev == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + username + " is not a developer.");
            outputs.add(error);
            return;
        }
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

        if (ticket.getStatus() != Status.OPEN) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Only OPEN tickets can be assigned.");
            outputs.add(error);
            return;
        }

        Milestone milestone = db.getMilestoneForTicket(ticketId);
        if (milestone == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The ticket " + ticketId + " is not assigned to any milestone.");
            outputs.add(error);
            return;
        }

        if (!milestone.getAssignedDevs().contains(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Developer " + username + " is not assigned to milestone "
                    + milestone.getName() + ".");
            outputs.add(error);
            return;
        }

        // Cannot assign tickets from a milestone that is blocked by unresolved dependencies
        if (milestone.isBlocked()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Cannot assign ticket " + ticketId + " from blocked milestone "
                    + milestone.getName() + ".");
            outputs.add(error);
            return;
        }
        LocalDate date = LocalDate.parse(input.getTimestamp());
        if (date.isBefore(milestone.getStartDate())) {
            CommandErrors.add(input, outputs,
                    "Cannot assign tickets before the milestone start date.");
            return;
        }
        db.refreshMilestone(milestone, date);

        Expertise ticketCategory = ticket.getCategory();
        // Verify compatibility between the developer's expertise and the ticket category
        if (!SecurityManager.hasExpertise(dev.getExpertise(), ticketCategory)) {
            List<String> required = SecurityManager.requiredExpertise(ticketCategory);

            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Developer " + username + " cannot assign ticket " + ticketId
                    + " due to expertise area. Required: " + String.join(", ", required)
                    + "; Current: " + dev.getExpertise() + ".");
            outputs.add(error);
            return;
        }

        /* Juniors can only take Low/Medium tickets of type Bug/UI;
        Mid and Senior developers have extended access
         */
        if (!SecurityManager.hasSeniority(dev.getSeniority(), ticket)) {
            List<String> required = SecurityManager.requiredSeniority(ticket);

            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Developer " + username + " cannot assign ticket " + ticketId
                    + " due to seniority level. Required: " + String.join(", ", required)
                    + "; Current: " + dev.getSeniority() + ".");
            outputs.add(error);
            return;
        }
        ticket.assignTo(username, input.getTimestamp());
    }
}
