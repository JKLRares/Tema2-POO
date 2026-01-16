package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GeneratePerformanceReportCommand implements Command {

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

        ManagerUser manager = (ManagerUser) user;
        LocalDate currentDate = LocalDate.parse(input.timestamp);

        YearMonth prevMonth = YearMonth.from(currentDate).minusMonths(1);
        LocalDate startOfPrevMonth = prevMonth.atDay(1);
        LocalDate endOfPrevMonth = prevMonth.atEndOfMonth();

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        List<ObjectNode> devReports = new ArrayList<>();

        if (manager.getSubordinates() != null) {
            for (String subUsername : manager.getSubordinates()) {
                User subUser = db.getUser(subUsername);

                if (subUser instanceof DeveloperUser) {
                    DeveloperUser dev = (DeveloperUser) subUser;
                    ObjectNode devNode = calculateDeveloperPerformance(dev, db, startOfPrevMonth, endOfPrevMonth);
                    devReports.add(devNode);
                }
            }
        }

        devReports.sort(Comparator.comparing(node -> node.get("username").asText()));

        ArrayNode reportArray = mapper.createArrayNode();
        devReports.forEach(reportArray::add);

        resultNode.set("report", reportArray);
        outputs.add(resultNode);
    }

    private ObjectNode calculateDeveloperPerformance(DeveloperUser dev, Database db, LocalDate start, LocalDate end) {
        List<Ticket> resolvedTickets = db.getTickets().stream()
                .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().equals(dev.getUsername()))
                .filter(t -> t.getStatus() == Status.CLOSED)
                .filter(t -> {
                    String solvedAtStr = getLastResolvedDate(t);
                    if (solvedAtStr == null || solvedAtStr.isEmpty()) return false;
                    LocalDate solved = LocalDate.parse(solvedAtStr);
                    return !solved.isBefore(start) && !solved.isAfter(end);
                })
                .toList();

        int closedTicketsCount = resolvedTickets.size();
        double performanceScore = 0.0;
        double averageResolutionTime = 0.0;

        if (closedTicketsCount > 0) {
            double totalResolutionDays = 0;
            for (Ticket t : resolvedTickets) {
                String solvedAt = getLastResolvedDate(t);
                if (t.getAssignedAt() != null && !t.getAssignedAt().isEmpty() && t.getSolvedAt() != null) {
                    LocalDate tStart = LocalDate.parse(t.getAssignedAt());
                    LocalDate tEnd = LocalDate.parse(solvedAt);
                    long days = ChronoUnit.DAYS.between(tStart, tEnd) + 1;
                    if (days <= 0) days = 1;
                    totalResolutionDays += days;
                }
            }
            averageResolutionTime = totalResolutionDays / closedTicketsCount;
        }

        if (closedTicketsCount > 0) {
            if (dev.getSeniority() == Seniority.JUNIOR) {
                long bugTickets = resolvedTickets.stream().filter(t -> t.getType() == TicketType.BUG).count();
                long featureTickets = resolvedTickets.stream().filter(t -> t.getType() == TicketType.FEATURE_REQUEST).count();
                long uiTickets = resolvedTickets.stream().filter(t -> t.getType() == TicketType.UI_FEEDBACK).count();

                double avgResolvedTicketType = (bugTickets + featureTickets + uiTickets) / 3.0;

                double variance = (Math.pow(bugTickets - avgResolvedTicketType, 2) +
                        Math.pow(featureTickets - avgResolvedTicketType, 2) +
                        Math.pow(uiTickets - avgResolvedTicketType, 2)) / 3.0;
                double standardDeviation = Math.sqrt(variance);

                double ticketDiversityFactor = (avgResolvedTicketType == 0) ? 0 : (standardDeviation / avgResolvedTicketType);
                double seniorityBonus = 5.0;

                performanceScore = Math.max(0, 0.5 * closedTicketsCount - ticketDiversityFactor) + seniorityBonus;
            } else if (dev.getSeniority() == Seniority.MID) {
                long highPriorityTickets = resolvedTickets.stream()
                        .filter(t -> t.getType() != TicketType.BUG)
                        .filter(t -> t.getPriority() == Priority.HIGH || t.getPriority() == Priority.CRITICAL)
                        .count();

                double seniorityBonus = 15.0;
                performanceScore = Math.max(0, 0.5 * closedTicketsCount + 0.7 * highPriorityTickets - 0.3 * averageResolutionTime) + seniorityBonus;

            } else if (dev.getSeniority() == Seniority.SENIOR) {
                long highPriorityTickets = resolvedTickets.stream()
                        .filter(t -> t.getPriority() == Priority.MEDIUM || t.getPriority() == Priority.HIGH || t.getPriority() == Priority.CRITICAL)
                        .count();

                double seniorityBonus = 30.0;
                performanceScore = Math.max(0, 0.5 * closedTicketsCount + 1.0 * highPriorityTickets - 0.5 * averageResolutionTime) + seniorityBonus;
            }
        }

        dev.setPerformanceScore(Math.round(performanceScore * 100.0) / 100.0);

        ObjectNode node = mapper.createObjectNode();
        node.put("username", dev.getUsername());
        node.put("closedTickets", closedTicketsCount);
        node.put("averageResolutionTime", Math.round(averageResolutionTime * 100.0) / 100.0);
        node.put("performanceScore", Math.round(performanceScore * 100.0) / 100.0);
        node.put("seniority", dev.getSeniority().toString());

        return node;
    }

    private String getLastResolvedDate(Ticket t) {
        if (t.getHistory() != null) {
            for (int i = t.getHistory().size() - 1; i >= 0; i--) {
                TicketHistoryEntry entry = t.getHistory().get(i);
                if (entry.getAction() == HistoryActionType.STATUS_CHANGED &&
                        entry.getToStatus() == Status.RESOLVED) {
                    return entry.getTimestamp();
                }
            }
        }
        return t.getSolvedAt();
    }
}