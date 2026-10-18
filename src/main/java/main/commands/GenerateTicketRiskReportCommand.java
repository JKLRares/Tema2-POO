package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.enums.Status;
import main.enums.UserRole;
import main.users.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenerateTicketRiskReportCommand implements Command {

    private static final int SMALL_RISK_FACTOR = 24;
    private static final int MID_RISK_FACTOR = 49;
    private static final int BIG_RISK_FACTOR = 74;

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

        Map<String, Double> totalRiskSumByType = new LinkedHashMap<>();
        Map<String, Integer> countForRiskByType = new LinkedHashMap<>();

        String[] types = {"BUG", "FEATURE_REQUEST", "UI_FEEDBACK"};
        for (String type : types) {
            totalRiskSumByType.put(type, 0.0);
            countForRiskByType.put(type, 0);
        }

        for (Ticket t : db.getTickets()) {
            if (t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS) {
                totalTickets++;

                String typeStr = t.getType().toString();
                ticketsByType.put(typeStr, ticketsByType.getOrDefault(typeStr, 0) + 1);

                String priorityStr = t.getPriority().toString();
                ticketsByPriority.put(priorityStr,
                        ticketsByPriority.getOrDefault(priorityStr, 0) + 1);

                double risk = t.calculateRisk();
                if (totalRiskSumByType.containsKey(typeStr)) {
                    totalRiskSumByType.put(typeStr, totalRiskSumByType.get(typeStr) + risk);
                    countForRiskByType.put(typeStr, countForRiskByType.get(typeStr) + 1);
                }
            }
        }

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        ObjectNode reportNode = mapper.createObjectNode();

        reportNode.put("totalTickets", totalTickets);

        ObjectNode ticketsByTypeNode = mapper.createObjectNode();
        ticketsByType.forEach(ticketsByTypeNode::put);
        reportNode.set("ticketsByType", ticketsByTypeNode);

        ObjectNode ticketsByPriorityNode = mapper.createObjectNode();
        ticketsByPriority.forEach(ticketsByPriorityNode::put);
        reportNode.set("ticketsByPriority", ticketsByPriorityNode);

        ObjectNode riskByTypeNode = mapper.createObjectNode();
        for (String type : types) {
            double totalRisk = totalRiskSumByType.get(type);
            int count = countForRiskByType.get(type);

            double avgRisk = (count > 0) ? (totalRisk / count) : 0.0;
            String qualifier = getRiskQualifier(avgRisk);

            riskByTypeNode.put(type, qualifier);
        }
        reportNode.set("riskByType", riskByTypeNode);

        resultNode.set("report", reportNode);
        outputs.add(resultNode);
    }

    private String getRiskQualifier(final double riskValue) {
        long roundedRisk = Math.round(riskValue);

        if (roundedRisk <= SMALL_RISK_FACTOR) {
            return "NEGLIGIBLE";
        } else if (roundedRisk <= MID_RISK_FACTOR) {
            return "MODERATE";
        } else if (roundedRisk <= BIG_RISK_FACTOR) {
            return "SIGNIFICANT";
        } else {
            return "MAJOR";
        }
    }
}
