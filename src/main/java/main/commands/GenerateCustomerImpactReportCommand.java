package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.enums.Status;
import main.enums.UserRole;
import main.users.User;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenerateCustomerImpactReportCommand implements Command {

    private static final double HUNDRED = 100.0;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        User user = db.getUser(input.getUsername());

        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + input.getUsername() + " does not exist.");
            outputs.add(error);
            return;
        }

        if (user.getRole() != UserRole.MANAGER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + input.getUsername()
                    + " does not have permission to execute this command: "
                    + "required role MANAGER; user role " + user.getRole() + ".");
            outputs.add(error);
            return;
        }

        int totalTickets = 0;

        Map<String, Integer> ticketsByType = new LinkedHashMap<>();
        ticketsByType.put("BUG", 0);
        ticketsByType.put("FEATURE_REQUEST", 0);
        ticketsByType.put("UI_FEEDBACK", 0);

        Map<String, Integer> ticketsByPriority = new LinkedHashMap<>();
        ticketsByPriority.put("LOW", 0);
        ticketsByPriority.put("MEDIUM", 0);
        ticketsByPriority.put("HIGH", 0);
        ticketsByPriority.put("CRITICAL", 0);

        Map<String, Double> totalImpactByType = new HashMap<>();
        Map<String, Integer> countForImpact = new HashMap<>();

        for (Ticket t : db.getTickets()) {
            if (t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS) {
                totalTickets++;

                String typeStr = t.getType().toString();
                ticketsByType.put(typeStr, ticketsByType.getOrDefault(typeStr, 0) + 1);

                String priorityStr = t.getPriority().toString();
                ticketsByPriority.put(priorityStr, ticketsByPriority
                        .getOrDefault(priorityStr, 0) + 1);

                double impact = t.calculateImpact();
                totalImpactByType.put(typeStr, totalImpactByType
                        .getOrDefault(typeStr, 0.0) + impact);
                countForImpact.put(typeStr, countForImpact.getOrDefault(typeStr, 0) + 1);
            }
        }

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        ObjectNode reportNode = mapper.createObjectNode();
        reportNode.put("totalTickets", totalTickets);

        ObjectNode typeNode = mapper.createObjectNode();
        ticketsByType.forEach(typeNode::put);
        reportNode.set("ticketsByType", typeNode);

        ObjectNode priorityNode = mapper.createObjectNode();
        ticketsByPriority.forEach(priorityNode::put);
        reportNode.set("ticketsByPriority", priorityNode);

        ObjectNode impactNode = mapper.createObjectNode();
        for (String type : ticketsByType.keySet()) {
            double totalImpact = totalImpactByType.getOrDefault(type, 0.0);
            int count = countForImpact.getOrDefault(type, 0);
            double avgImpact = (count > 0) ? (totalImpact / count) : 0.0;
            impactNode.put(type, Math.round(avgImpact * HUNDRED) / HUNDRED);
        }
        reportNode.set("customerImpactByType", impactNode);

        resultNode.set("report", reportNode);
        outputs.add(resultNode);
    }
}
