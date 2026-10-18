package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.Milestone;
import main.tickets.Ticket;
import main.tickets.TicketHistoryEntry;
import main.enums.HistoryActionType;
import main.enums.UserRole;
import main.users.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ViewTicketHistoryCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        User user = db.getUser(input.getUsername());

        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("status", "error");
            error.put("description", "User not found: " + input.getUsername());
            outputs.add(error);
            return;
        }

        if (user.getRole() == UserRole.REPORTER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user does not have permission to execute this command: "
                    + "required role DEVELOPER, MANAGER; user role REPORTER.");
            outputs.add(error);
            return;
        }

        List<Ticket> filteredTickets = new ArrayList<>();

        if (user.getRole() == UserRole.DEVELOPER) {
            for (Ticket t : db.getTickets()) {
                boolean isCurrentlyAssigned = t.getAssignedTo() != null
                        && t.getAssignedTo().equals(input.getUsername());
                boolean hasHistoryInteraction = t.getHistory().stream()
                        .anyMatch(entry -> (input.getUsername().equals(entry.getUser())
                        || input.getUsername().equals(entry.getRemovedFrom())));

                if (isCurrentlyAssigned || hasHistoryInteraction) {
                    filteredTickets.add(t);
                }
            }
        } else if (user.getRole() == UserRole.MANAGER) {
            for (Milestone m : db.getMilestones()) {
                if (m.getCreator().equals(input.getUsername())) {
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
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        ArrayNode ticketsHistoryArray = mapper.createArrayNode();

        for (Ticket t : filteredTickets) {
            ObjectNode ticketNode = mapper.createObjectNode();

            ticketNode.put("id", t.getId());
            ticketNode.put("title", t.getTitle());
            ticketNode.put("status", t.getStatus().toString());

            List<TicketHistoryEntry> processingHistory = new ArrayList<>(t.getHistory());

            if (user.getRole() == UserRole.DEVELOPER
                    && !input.getUsername().equals(t.getAssignedTo())) {
                for (int i = processingHistory.size() - 1; i >= 0; i--) {
                    TicketHistoryEntry entry = processingHistory.get(i);
                    if ((entry.getAction() == HistoryActionType.DE_ASSIGNED
                            || entry.getAction() == HistoryActionType.REMOVED_FROM_DEV)
                            && (input.getUsername().equals(entry.getUser())
                        || input.getUsername().equals(entry.getRemovedFrom()))) {
                        processingHistory = processingHistory.subList(0, i + 1);
                        break;
                    }
                }
            }

            ArrayNode actionsArray = mapper.createArrayNode();
            for (TicketHistoryEntry entry : processingHistory) {
                ObjectNode entryNode = mapper.valueToTree(entry);

                entryNode.remove("details");

                if (entry.getMilestone() == null) {
                    entryNode.remove("milestone");
                }
                if (entry.getFrom() == null) {
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
