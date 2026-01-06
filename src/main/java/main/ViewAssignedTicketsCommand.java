package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ViewAssignedTicketsCommand implements Command {

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

        List<Ticket> filteredTickets = db.getTickets().stream()
                .filter(ticket -> ticket.getAssignedTo().equals(input.username))
                .collect(Collectors.toList());

        filteredTickets.sort(Comparator.comparing(Ticket::getPriority, Comparator.reverseOrder())
                .thenComparing(Ticket::getCreatedAt)
                .thenComparing(Ticket::getId));

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

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