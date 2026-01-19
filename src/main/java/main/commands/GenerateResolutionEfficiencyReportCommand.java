package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.enums.Status;
import main.enums.UserRole;
import main.users.User;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenerateResolutionEfficiencyReportCommand implements Command {

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
            boolean isResolved = (t.getStatus() == Status.RESOLVED
                    || t.getStatus() == Status.CLOSED);
            boolean hasDates = (t.getAssignedAt() != null && !t.getAssignedAt().isEmpty())
                    && (t.getSolvedAt() != null && !t.getSolvedAt().isEmpty());

            if (isResolved && hasDates) {
                long daysToResolve = calculateDays(t.getAssignedAt(), t.getSolvedAt());

                if (daysToResolve <= 0) {
                    daysToResolve = 1;
                }

                double efficiency = t.calculateEfficiency(daysToResolve);

                if (efficiency >= 0) {
                    String typeStr = t.getType().toString();
                    totalEfficiencySumByType.put(typeStr, totalEfficiencySumByType
                            .getOrDefault(typeStr, 0.0) + efficiency);
                    countForEfficiencyByType.put(typeStr, countForEfficiencyByType
                            .getOrDefault(typeStr, 0) + 1);
                    ticketsByType.put(typeStr, ticketsByType.get(typeStr) + 1);
                    String priorityStr = t.getPriority().toString();
                    ticketsByPriority.put(priorityStr, ticketsByPriority
                            .getOrDefault(priorityStr, 0) + 1);
                    totalConsideredTickets++;
                }
            }
        }

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

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

            efficiencyByTypeNode.put(type, Math.round(avgEff * HUNDRED) / HUNDRED);
        }
        reportNode.set("efficiencyByType", efficiencyByTypeNode);

        resultNode.set("report", reportNode);
        outputs.add(resultNode);
    }

    private long calculateDays(final String assignedAtStr, final String solvedAtStr) {
        LocalDate start = LocalDate.parse(assignedAtStr);
        LocalDate end = LocalDate.parse(solvedAtStr);
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}
