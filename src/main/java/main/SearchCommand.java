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
import java.util.Map;
import java.util.stream.Collectors;

public class SearchCommand implements Command {

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

        if (user.getRole() == UserRole.REPORTER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not have permission to execute this command: required role DEVELOPER or MANAGER.");
            outputs.add(error);
            return;
        }

        Map<String, Object> filters = (Map<String, Object>) input.params.get("filters");
        if (filters == null) {
            filters = Map.of();
        }

        String searchType = (String) filters.getOrDefault("searchType", "TICKET");

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);
        resultNode.put("searchType", searchType);

        ArrayNode resultsArray = mapper.createArrayNode();

        if ("TICKET".equals(searchType)) {
            List<Ticket> tickets = getTicketsToSearch(user, db);

            tickets.sort(Comparator.comparing(Ticket::getCreatedAt)
                    .thenComparing(Ticket::getId));

            List<String> searchKeywords = new ArrayList<>();
            if (user.getRole() == UserRole.MANAGER && filters.containsKey("keywords")) {
                Object k = filters.get("keywords");
                if (k instanceof List<?>) {
                    for (Object o : (List<?>) k) {
                        searchKeywords.add(o.toString());
                    }
                } else if (k != null) {
                    searchKeywords.add(k.toString());
                }
            }

            for (Ticket t : tickets) {
                if (!matchesStandardFilters(t, filters)) {
                    continue;
                }

                if (user.getRole() == UserRole.MANAGER) {
                    if (filters.containsKey("expertiseArea")) {
                        Object valObj = filters.get("expertiseArea");
                        if (valObj != null) {
                            String val = valObj.toString();
                            if (t.getCategory() == null || !t.getCategory().toString().equals(val)) {
                                continue;
                            }
                        }
                    }
                }

                if (user.getRole() == UserRole.DEVELOPER) {
                    if (!SecurityManager.getInstance().hasAccess(user, t)) {
                        continue;
                    }
                }

                List<String> foundWords = new ArrayList<>();
                if (!searchKeywords.isEmpty()) {
                    boolean match = false;
                    String title = t.getTitle() != null ? t.getTitle().toLowerCase() : "";
                    String desc = t.getDescription() != null ? t.getDescription().toLowerCase() : "";

                    for (String keyword : searchKeywords) {
                        String kLower = keyword.toLowerCase();
                        if (title.contains(kLower) || desc.contains(kLower)) {
                            match = true;
                            foundWords.add(keyword);
                        }
                    }
                    if (!match) {
                        continue;
                    }
                }

                resultsArray.add(ticketToNode(t, foundWords, user.getRole() == UserRole.MANAGER));
            }
        } else if ("DEVELOPER".equals(searchType)) {
            if (user.getRole() == UserRole.MANAGER) {
                ManagerUser manager = (ManagerUser) user;
                List<DeveloperUser> devs = getDevelopersToSearch(manager, db);
                List<DeveloperUser> filteredDevs = filterDevelopers(devs, filters, input.timestamp, db);

                filteredDevs.sort(Comparator.comparing(User::getUsername));

                LocalDate currentDate = LocalDate.parse(input.timestamp);
                YearMonth prevMonth = YearMonth.from(currentDate).minusMonths(1);
                LocalDate start = prevMonth.atDay(1);
                LocalDate end = prevMonth.atEndOfMonth();

                for (DeveloperUser dev : filteredDevs) {
                    double score = dev.getPerformanceScore();
                    resultsArray.add(developerToNode(dev, score));
                }
            }
        }

        resultNode.set("results", resultsArray);
        outputs.add(resultNode);
    }

    private ObjectNode ticketToNode(Ticket t, List<String> matchingWords, boolean isManager) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", t.getId());
        node.put("type", t.getType().toString());
        node.put("title", t.getTitle());
        node.put("businessPriority", t.getPriority().toString());
        node.put("status", t.getStatus().toString());
        node.put("createdAt", t.getCreatedAt());
        node.put("solvedAt", t.getSolvedAt() != null ? t.getSolvedAt() : "");
        node.put("reportedBy", t.getReportedBy());

        if (isManager) {
            ArrayNode mw = mapper.createArrayNode();
            if (matchingWords != null) {
                matchingWords.forEach(mw::add);
            }
            node.set("matchingWords", mw);
        }
        return node;
    }

    private boolean matchesStandardFilters(Ticket t, Map<String, Object> filters) {
        if (filters.containsKey("businessPriority")) {
            String val = (String) filters.get("businessPriority");
            if (!t.getPriority().toString().equals(val)) return false;
        }

        if (filters.containsKey("type")) {
            String val = (String) filters.get("type");
            if (!t.getType().toString().equals(val)) return false;
        }

        if (filters.containsKey("createdAt")) {
            String val = (String) filters.get("createdAt");
            if (!t.getCreatedAt().equals(val)) return false;
        }

        if (filters.containsKey("createdAfter")) {
            String val = (String) filters.get("createdAfter");
            LocalDate filterDate = LocalDate.parse(val);
            LocalDate ticketDate = LocalDate.parse(t.getCreatedAt());
            if (!ticketDate.isAfter(filterDate)) return false;
        }

        if (filters.containsKey("createdBefore")) {
            String val = (String) filters.get("createdBefore");
            LocalDate filterDate = LocalDate.parse(val);
            LocalDate ticketDate = LocalDate.parse(t.getCreatedAt());
            if (!ticketDate.isBefore(filterDate)) return false;
        }

        if (filters.containsKey("availableForAssignment")) {
            boolean required = (Boolean) filters.get("availableForAssignment");
            if (required) {
                boolean isUnassigned = (t.getAssignedTo() == null || t.getAssignedTo().isEmpty());
                boolean isOpen = (t.getStatus() == Status.OPEN);
                if (!isUnassigned || !isOpen) return false;
            }
        }
        return true;
    }

    private ObjectNode developerToNode(DeveloperUser dev, double score) {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", dev.getUsername());
        node.put("expertiseArea", dev.getExpertise().toString());
        node.put("seniority", dev.getSeniority().toString());
        node.put("performanceScore", score);
        if (dev.getHireDate() != null) {
            node.put("hireDate", dev.getHireDate().toString());
        }
        return node;
    }

    private List<Ticket> getTicketsToSearch(User user, Database db) {
        if (user.getRole() == UserRole.MANAGER) {
            return db.getTickets();
        } else if (user.getRole() == UserRole.DEVELOPER) {
            return db.getMilestones().stream()
                    .filter(m -> m.getAssignedDevs() != null && m.getAssignedDevs().contains(user.getUsername()))
                    .flatMap(m -> m.getTickets().stream())
                    .filter(t -> t.getStatus() == Status.OPEN)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<DeveloperUser> getDevelopersToSearch(ManagerUser manager, Database db) {
        List<DeveloperUser> devs = new ArrayList<>();
        if (manager.getSubordinates() != null) {
            for (String subName : manager.getSubordinates()) {
                User u = db.getUser(subName);
                if (u instanceof DeveloperUser) {
                    devs.add((DeveloperUser) u);
                }
            }
        }
        return devs;
    }

    private List<DeveloperUser> filterDevelopers(List<DeveloperUser> devs, Map<String, Object> filters, String timestamp, Database db) {
        List<DeveloperUser> results = new ArrayList<>();

        LocalDate currentDate = LocalDate.parse(timestamp);
        YearMonth prevMonth = YearMonth.from(currentDate).minusMonths(1);
        LocalDate startOfPrevMonth = prevMonth.atDay(1);
        LocalDate endOfPrevMonth = prevMonth.atEndOfMonth();

        for (DeveloperUser dev : devs) {
            boolean matches = true;

            if (filters.containsKey("expertiseArea")) {
                String val = (String) filters.get("expertiseArea");
                if (!dev.getExpertise().toString().equals(val)) matches = false;
            }

            if (filters.containsKey("seniority")) {
                String val = (String) filters.get("seniority");
                if (!dev.getSeniority().toString().equals(val)) matches = false;
            }

            if (filters.containsKey("keywords")) {
                String keyword = ((String) filters.get("keywords")).toLowerCase();
                String username = dev.getUsername().toLowerCase();
                String email = dev.getEmail() != null ? dev.getEmail().toLowerCase() : "";
                if (!username.contains(keyword) && !email.contains(keyword)) {
                    matches = false;
                }
            }

            if (filters.containsKey("createdAt")) {
                String val = (String) filters.get("createdAt");
                if (dev.getHireDate() == null || !dev.getHireDate().toString().equals(val)) {
                    matches = false;
                }
            }

            if (filters.containsKey("createdAfter")) {
                String val = (String) filters.get("createdAfter");
                if (dev.getHireDate() != null) {
                    LocalDate filterDate = LocalDate.parse(val);
                    if (!dev.getHireDate().isAfter(filterDate)) matches = false;
                } else {
                    matches = false;
                }
            }

            if (filters.containsKey("createdBefore")) {
                String val = (String) filters.get("createdBefore");
                if (dev.getHireDate() != null) {
                    LocalDate filterDate = LocalDate.parse(val);
                    if (!dev.getHireDate().isBefore(filterDate)) matches = false;
                } else {
                    matches = false;
                }
            }

            if (filters.containsKey("performanceScoreAbove") || filters.containsKey("performanceScoreBelow")) {
                double score = dev.getPerformanceScore();

                if (filters.containsKey("performanceScoreAbove")) {
                    double limit = ((Number) filters.get("performanceScoreAbove")).doubleValue();
                    if (score < limit) matches = false;
                }

                if (filters.containsKey("performanceScoreBelow")) {
                    double limit = ((Number) filters.get("performanceScoreBelow")).doubleValue();
                    if (score >= limit) matches = false;
                }
            }

            if (matches) {
                results.add(dev);
            }
        }
        return results;
    }

    private double calculatePerformanceScore(DeveloperUser dev, Database db, LocalDate start, LocalDate end) {
        List<Ticket> resolvedTickets = db.getTickets().stream()
                .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().equals(dev.getUsername()))
                .filter(t -> t.getStatus() == Status.CLOSED)
                .filter(t -> {
                    String solvedAtStr = getLastResolvedDate(t);
                    if (solvedAtStr == null || solvedAtStr.isEmpty()) return false;
                    LocalDate solved = LocalDate.parse(solvedAtStr);
                    return !solved.isBefore(start) && !solved.isAfter(end);
                })
                .collect(Collectors.toList());

        int closedTicketsCount = resolvedTickets.size();
        double performanceScore = 0.0;
        double averageResolutionTime = 0.0;

        if (closedTicketsCount > 0) {
            double totalResolutionDays = 0;
            for (Ticket t : resolvedTickets) {
                String solvedAt = getLastResolvedDate(t);
                if (t.getAssignedAt() != null && !t.getAssignedAt().isEmpty() && solvedAt != null) {
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

        return Math.round(performanceScore * 100.0) / 100.0;
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