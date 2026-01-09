package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ViewTicketHistoryCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);

        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("status", "error");
            error.put("description", "User not found: " + input.username);
            outputs.add(error);
            return;
        }

        if (user.getRole() == UserRole.REPORTER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not have permission to execute this command: required role DEVELOPER or MANAGER; user role REPORTER.");
            outputs.add(error);
            return;
        }

        List<Ticket> filteredTickets = new ArrayList<>();

        if (user.getRole() == UserRole.DEVELOPER) {
            for (Ticket t : db.getTickets()) {
                boolean isCurrentlyAssigned = t.getAssignedTo() != null && t.getAssignedTo().equals(input.username);
                boolean hasHistoryInteraction = t.getHistory().stream()
                        .anyMatch(entry -> entry.getUser().equals(input.username));

                if (isCurrentlyAssigned || hasHistoryInteraction) {
                    filteredTickets.add(t);
                }
            }
        } else if (user.getRole() == UserRole.MANAGER) {
            for (Milestone m : db.getMilestones()) {
                if (m.getCreator().equals(input.username)) {
                    filteredTickets.addAll(m.getTickets());
                }
            }
        }

        filteredTickets = filteredTickets.stream()
                .distinct()
                .collect(Collectors.toList());

        filteredTickets.sort(Comparator.comparing(Ticket::getCreatedAt)
                .thenComparing(Ticket::getId));

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        ArrayNode ticketsHistoryArray = mapper.createArrayNode();

        for (Ticket t : filteredTickets) {
            ObjectNode ticketNode = mapper.createObjectNode();

            ticketNode.put("id", t.getId());
            ticketNode.put("title", t.getTitle());
            ticketNode.put("status", t.getStatus().toString());

            List<TicketHistoryEntry> processingHistory = new ArrayList<>(t.getHistory());

            ArrayNode actionsArray = mapper.createArrayNode();
            for (TicketHistoryEntry entry : processingHistory) {
                ObjectNode entryNode = mapper.valueToTree(entry);

                entryNode.remove("details");

                if (entry.getMilestone() == null) {
                    entryNode.remove("milestone");
                }

                if (entry.getFromStatus() == null) {
                    entryNode.remove("from");
                }
                if (entry.getToStatus() == null) {
                    entryNode.remove("to");
                }

                actionsArray.add(entryNode);
            }

            ticketNode.set("actions", actionsArray);

            ticketNode.set("comments", mapper.valueToTree(t.getComments()));
            ticketsHistoryArray.add(ticketNode);
        }

        resultNode.set("ticketHistory", ticketsHistoryArray);
        outputs.add(resultNode);
    }
}