package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.tickets.TicketHistoryEntry;
import main.enums.*;
import main.users.DeveloperUser;
import main.users.ManagerUser;
import main.users.User;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GeneratePerformanceReportCommand implements Command {

    private static final double TICKET_TYPE_COUNT = 3.0;
    private static final double JUNIOR_BONUS = 5.0;
    private static final double MID_BONUS = 15.0;
    private static final double SENIOR_BONUS = 30.0;
    private static final double PENALTY_WEIGHT = 0.5;
    private static final double HIGH_PRIORITY_WEIGHT = 0.7;
    private static final double RES_TIME_WEIGHT = 0.3;
    private static final double ROUNDING_MULT = 100.0;

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

        ManagerUser manager = user.asManager();
        if (manager == null) {
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

        LocalDate currentDate = LocalDate.parse(input.getTimestamp());

        // The report is generated strictly for the month prior to the current timestamp
        YearMonth prevMonth = YearMonth.from(currentDate).minusMonths(1);
        LocalDate startOfPrevMonth = prevMonth.atDay(1);
        LocalDate endOfPrevMonth = prevMonth.atEndOfMonth();

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        List<ObjectNode> devReports = new ArrayList<>();

        if (manager.getSubordinates() != null) {
            for (String subUsername : manager.getSubordinates()) {
                User subUser = db.getUser(subUsername);

                DeveloperUser dev = subUser.asDeveloper();
                if (dev != null) {
                    ObjectNode devNode = calculateDeveloperPerformance(dev,
                            db, startOfPrevMonth, endOfPrevMonth);
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

    private ObjectNode calculateDeveloperPerformance(final DeveloperUser dev, final Database db,
                                                     final LocalDate start, final LocalDate end) {
        // Consider only tickets that are CLOSED and were actually resolved the previous month
        List<Ticket> resolvedTickets = db.getTickets().stream()
                .filter(t -> t.getAssignedTo() != null
                        && t.getAssignedTo().equals(dev.getUsername()))
                .filter(t -> t.getStatus() == Status.CLOSED)
                .filter(t -> {
                    String solvedAtStr = getLastResolvedDate(t);
                    if (solvedAtStr == null || solvedAtStr.isEmpty()) {
                        return false;
                    }
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
                if (t.getAssignedAt() != null && !t.getAssignedAt().isEmpty()
                        && t.getSolvedAt() != null) {
                    LocalDate tStart = LocalDate.parse(t.getAssignedAt());
                    LocalDate tEnd = LocalDate.parse(solvedAt);
                    long days = ChronoUnit.DAYS.between(tStart, tEnd) + 1;
                    if (days <= 0) {
                        days = 1;
                    }
                    totalResolutionDays += days;
                }
            }
            averageResolutionTime = totalResolutionDays / closedTicketsCount;
        }

        if (closedTicketsCount > 0) {
            if (dev.getSeniority() == Seniority.JUNIOR) {
                long bugTickets = resolvedTickets.stream()
                        .filter(t -> t.getType() == TicketType.BUG).count();
                long featureTickets = resolvedTickets.stream()
                        .filter(t -> t.getType() == TicketType.FEATURE_REQUEST).count();
                long uiTickets = resolvedTickets.stream()
                        .filter(t -> t.getType() == TicketType.UI_FEEDBACK).count();

                double avgResolvedTicketType = (bugTickets + featureTickets + uiTickets)
                        / TICKET_TYPE_COUNT;

                // For juniors the diversity of resolved ticket types negatively impacts the score
                double variance = (Math.pow(bugTickets - avgResolvedTicketType, 2)
                        + Math.pow(featureTickets - avgResolvedTicketType, 2)
                        + Math.pow(uiTickets - avgResolvedTicketType, 2)) / TICKET_TYPE_COUNT;
                double standardDeviation = Math.sqrt(variance);

                double ticketDiversityFactor = (avgResolvedTicketType == 0) ? 0
                        : (standardDeviation / avgResolvedTicketType);
                double seniorityBonus = JUNIOR_BONUS;

                performanceScore = Math.max(0, PENALTY_WEIGHT * closedTicketsCount
                        - ticketDiversityFactor) + seniorityBonus;
            } else if (dev.getSeniority() == Seniority.MID) {
                long highPriorityTickets = resolvedTickets.stream()
                        .filter(t -> t.getType() != TicketType.BUG)
                        .filter(t -> t.getPriority() == Priority.HIGH
                                || t.getPriority() == Priority.CRITICAL)
                        .count();

                double seniorityBonus = MID_BONUS;
                performanceScore = Math.max(0, PENALTY_WEIGHT * closedTicketsCount
                        + HIGH_PRIORITY_WEIGHT * highPriorityTickets
                        - RES_TIME_WEIGHT * averageResolutionTime) + seniorityBonus;

            } else if (dev.getSeniority() == Seniority.SENIOR) {
                long highPriorityTickets = resolvedTickets.stream()
                        .filter(t -> t.getPriority() == Priority.MEDIUM
                                || t.getPriority() == Priority.HIGH
                                || t.getPriority() == Priority.CRITICAL)
                        .count();

                double seniorityBonus = SENIOR_BONUS;
                performanceScore = Math.max(0, PENALTY_WEIGHT * closedTicketsCount
                        + 1.0 * highPriorityTickets
                        - PENALTY_WEIGHT * averageResolutionTime) + seniorityBonus;
            }
        }

        dev.setPerformanceScore(Math.round(performanceScore * ROUNDING_MULT) / ROUNDING_MULT);

        ObjectNode node = mapper.createObjectNode();
        node.put("username", dev.getUsername());
        node.put("closedTickets", closedTicketsCount);
        node.put("averageResolutionTime", Math.round(averageResolutionTime * ROUNDING_MULT) / ROUNDING_MULT);
        node.put("performanceScore", Math.round(performanceScore * ROUNDING_MULT) / ROUNDING_MULT);
        node.put("seniority", dev.getSeniority().toString());

        return node;
    }

    private String getLastResolvedDate(final Ticket t) {
        if (t.getHistory() != null) {
            for (int i = t.getHistory().size() - 1; i >= 0; i--) {
                TicketHistoryEntry entry = t.getHistory().get(i);
                if (entry.getAction() == HistoryActionType.STATUS_CHANGED
                        && entry.getToStatus() == Status.RESOLVED) {
                    return entry.getTimestamp();
                }
            }
        }
        return t.getSolvedAt();
    }
}
