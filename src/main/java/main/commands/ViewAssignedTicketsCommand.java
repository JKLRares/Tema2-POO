package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.users.User;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ViewAssignedTicketsCommand implements Command {

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

        List<Ticket> filteredTickets = db.getTickets().stream()
                .filter(ticket -> ticket.getAssignedTo().equals(input.getUsername()))
                .collect(Collectors.toList());

        filteredTickets.sort(Comparator.comparing(Ticket::getPriority, Comparator.reverseOrder())
                .thenComparing(Ticket::getCreatedAt)
                .thenComparing(Ticket::getId));

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        ArrayNode ticketsArray = mapper.createArrayNode();
        for (Ticket t : filteredTickets) {
            ObjectNode ticketNode = mapper.valueToTree(t);
            ticketNode.remove("assignedTo");
            ticketNode.remove("solvedAt");
            ticketsArray.add(ticketNode);
        }

        resultNode.set("assignedTickets", ticketsArray);

        outputs.add(resultNode);
    }
}
