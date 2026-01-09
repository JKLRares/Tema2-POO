package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenerateResolutionEfficiencyReportCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);

        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not exist.");
            outputs.add(error);
            return;
        }

        if (user.getRole() != UserRole.MANAGER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".");
            outputs.add(error);
            return;
        }

        int totalConsideredTickets = 0;

        Map<String, Double> totalEfficiencySumByType = new LinkedHashMap<>();
        Map<String, Integer> countForEfficiencyByType = new LinkedHashMap<>();
        Map<String, Integer> ticketsByType = new LinkedHashMap<>();
        Map<String, Integer> ticketsByPriority = new LinkedHashMap<>();

        String[] types = {"BUG", "FEATURE_REQUEST", "UI_FEEDBACK"};
        for (String type : types) {
            totalEfficiencySumByType.put(type, 0.0);
            countForEfficiencyByType.put(type, 0);
            ticketsByType.put(type, 0);
        }

        ticketsByPriority.put("LOW", 0);
        ticketsByPriority.put("MEDIUM", 0);
        ticketsByPriority.put("HIGH", 0);
        ticketsByPriority.put("CRITICAL", 0);

        for (Ticket t : db.getTickets()) {
            boolean isResolved = (t.getStatus() == Status.RESOLVED || t.getStatus() == Status.CLOSED);
            boolean hasDates = (t.getAssignedAt() != null && !t.getAssignedAt().isEmpty()) &&
                    (t.getSolvedAt() != null && !t.getSolvedAt().isEmpty());

            if (isResolved && hasDates) {
                long daysToResolve = calculateDays(t.getAssignedAt(), t.getSolvedAt());

                if (daysToResolve <= 0) daysToResolve = 1;

                double efficiency = t.calculateEfficiency(daysToResolve);

                if (efficiency >= 0) {
                    String typeStr = t.getType().toString();
                    totalEfficiencySumByType.put(typeStr, totalEfficiencySumByType.getOrDefault(typeStr, 0.0) + efficiency);
                    countForEfficiencyByType.put(typeStr, countForEfficiencyByType.getOrDefault(typeStr, 0) + 1);
                    ticketsByType.put(typeStr, ticketsByType.get(typeStr) + 1);
                    String priorityStr = t.getPriority().toString();
                    ticketsByPriority.put(priorityStr, ticketsByPriority.getOrDefault(priorityStr, 0) + 1);
                    totalConsideredTickets++;
                }
            }
        }

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        ObjectNode reportNode = mapper.createObjectNode();
        reportNode.put("totalTickets", totalConsideredTickets);

        ObjectNode ticketsByTypeNode = mapper.createObjectNode();
        for (String type : types) {
            ticketsByTypeNode.put(type, ticketsByType.get(type));
        }
        reportNode.set("ticketsByType", ticketsByTypeNode);

        ObjectNode ticketsByPriorityNode = mapper.createObjectNode();
        for (Map.Entry<String, Integer> entry : ticketsByPriority.entrySet()) {
            ticketsByPriorityNode.put(entry.getKey(), entry.getValue());
        }
        reportNode.set("ticketsByPriority", ticketsByPriorityNode);

        ObjectNode efficiencyByTypeNode = mapper.createObjectNode();
        for (String type : types) {
            double totalEff = totalEfficiencySumByType.get(type);
            int count = countForEfficiencyByType.get(type);

            double avgEff = (count > 0) ? (totalEff / count) : 0.0;

            efficiencyByTypeNode.put(type, Math.round(avgEff * 100.0) / 100.0);
        }
        reportNode.set("efficiencyByType", efficiencyByTypeNode);

        resultNode.set("report", reportNode);
        outputs.add(resultNode);
    }

    private long calculateDays(String assignedAtStr, String solvedAtStr) {
        LocalDate start = LocalDate.parse(assignedAtStr);
        LocalDate end = LocalDate.parse(solvedAtStr);
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}