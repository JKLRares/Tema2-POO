package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.enums.Status;
import main.enums.UserRole;
import main.users.DeveloperUser;
import main.users.ManagerUser;
import main.users.User;
import main.users.SecurityManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SearchCommand implements Command {
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

        if (user.getRole() == UserRole.REPORTER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + input.getUsername()
                    + " does not have permission to execute this command: "
                    + "required role DEVELOPER or MANAGER.");
            outputs.add(error);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> filters = (Map<String, Object>) input.getParams().get("filters");
        if (filters == null) {
            filters = Map.of();
        }

        String searchType = (String) filters.getOrDefault("searchType", "TICKET");

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());
        resultNode.put("searchType", searchType);

        ArrayNode resultsArray = mapper.createArrayNode();

        if ("TICKET".equals(searchType)) {
            List<Ticket> tickets = getTicketsToSearch(user, db);

            tickets.sort(Comparator.comparing(Ticket::getCreatedAt)
                    .thenComparing(Ticket::getId));

            List<String> searchKeywords = new ArrayList<>();
            if (user.getRole() == UserRole.MANAGER && filters.containsKey("keywords")) {
                Object k = filters.get("keywords");
                if (k != null) {
                    if (mapper.valueToTree(k).isArray()) {
                        ArrayNode array = mapper.valueToTree(k);
                        for (int i = 0; i < array.size(); i++) {
                            searchKeywords.add(array.get(i).asText());
                        }
                    } else {
                        searchKeywords.add(k.toString());
                    }
                }
            }

            for (Ticket t : tickets) {
                if (!matchesStandardFilters(t, filters)) {
                    continue;
                }

                /* Filter tickets based on role: Managers see everything, Developers see
                only assigned or accessible tickets
                */
                if (user.getRole() == UserRole.MANAGER) {
                    if (filters.containsKey("expertiseArea")) {
                        Object valObj = filters.get("expertiseArea");
                        if (valObj != null) {
                            String val = valObj.toString();
                            if (t.getCategory() == null
                                    || !t.getCategory().toString().equals(val)) {
                                continue;
                            }
                        }
                    }
                }

                if (user.getRole() == UserRole.DEVELOPER) {
                    if (!SecurityManager.hasAccess(user, t)) {
                        continue;
                    }
                }

                List<String> foundWords = new ArrayList<>();
                if (!searchKeywords.isEmpty()) {
                    LinkedHashSet<String> matched = new LinkedHashSet<>();
                    String text = (t.getTitle() + " "
                            + (t.getDescription() == null ? "" : t.getDescription()))
                            .toLowerCase(Locale.ROOT);
                    List<String> words = Pattern.compile("[\\p{L}\\p{N}]+")
                            .matcher(text).results().map(result -> result.group()).toList();
                    for (String keyword : searchKeywords) {
                        String needle = keyword.toLowerCase(Locale.ROOT);
                        words.stream().filter(word -> word.contains(needle)).forEach(matched::add);
                    }
                    foundWords.addAll(matched);
                    boolean match = !matched.isEmpty();
                    if (!match) {
                        continue;
                    }
                }

                resultsArray.add(ticketToNode(t, foundWords,
                        user.getRole() == UserRole.MANAGER));
            }
        } else if ("DEVELOPER".equals(searchType)) {
            ManagerUser manager = user.asManager();
            if (manager != null) {
                List<DeveloperUser> devs = getDevelopersToSearch(manager, db);
                List<DeveloperUser> filteredDevs = filterDevelopers(devs, filters,
                        input.getTimestamp(), db);

                filteredDevs.sort(Comparator.comparing(User::getUsername));

                LocalDate currentDate = LocalDate.parse(input.getTimestamp());
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

    private ObjectNode ticketToNode(final Ticket t, final List<String> matchingWords,
                                    final boolean isManager) {
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

    private boolean matchesStandardFilters(final Ticket t, final Map<String, Object> filters) {
        if (filters.containsKey("businessPriority")) {
            String val = (String) filters.get("businessPriority");
            if (!t.getPriority().toString().equals(val)) {
                return false;
            }
        }

        if (filters.containsKey("type")) {
            String val = (String) filters.get("type");
            if (!t.getType().toString().equals(val)) {
                return false;
            }
        }

        if (filters.containsKey("createdAt")) {
            String val = (String) filters.get("createdAt");
            if (!t.getCreatedAt().equals(val)) {
                return false;
            }
        }

        if (filters.containsKey("createdAfter")) {
            String val = (String) filters.get("createdAfter");
            LocalDate filterDate = LocalDate.parse(val);
            LocalDate ticketDate = LocalDate.parse(t.getCreatedAt());
            if (!ticketDate.isAfter(filterDate)) {
                return false;
            }
        }

        if (filters.containsKey("createdBefore")) {
            String val = (String) filters.get("createdBefore");
            LocalDate filterDate = LocalDate.parse(val);
            LocalDate ticketDate = LocalDate.parse(t.getCreatedAt());
            if (!ticketDate.isBefore(filterDate)) {
                return false;
            }
        }

        if (filters.containsKey("availableForAssignment")) {
            boolean required = (Boolean) filters.get("availableForAssignment");
            if (required) {
                boolean isUnassigned = (t.getAssignedTo() == null || t.getAssignedTo().isEmpty());
                boolean isOpen = (t.getStatus() == Status.OPEN);
                if (!isUnassigned || !isOpen) {
                    return false;
                }
            }
        }
        return true;
    }

    private ObjectNode developerToNode(final DeveloperUser dev, final double score) {
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

    private List<Ticket> getTicketsToSearch(final User user, final Database db) {
        if (user.getRole() == UserRole.MANAGER) {
            return db.getTickets();
        } else if (user.getRole() == UserRole.DEVELOPER) {
            return db.getMilestones().stream()
                    .filter(m -> m.getAssignedDevs() != null
                            && m.getAssignedDevs().contains(user.getUsername()))
                    .flatMap(m -> m.getTickets().stream())
                    .filter(t -> t.getStatus() == Status.OPEN)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<DeveloperUser> getDevelopersToSearch(final ManagerUser manager,
                                                      final Database db) {
        List<DeveloperUser> devs = new ArrayList<>();
        if (manager.getSubordinates() != null) {
            for (String subName : manager.getSubordinates()) {
                User u = db.getUser(subName);
                DeveloperUser dev = u == null ? null : u.asDeveloper();
                if (dev != null) {
                    devs.add(dev);
                }
            }
        }
        return devs;
    }

    private List<DeveloperUser> filterDevelopers(final List<DeveloperUser> devs,
                                                 final Map<String, Object> filters,
                                                 final String timestamp,
                                                 final Database db) {
        List<DeveloperUser> results = new ArrayList<>();

        LocalDate currentDate = LocalDate.parse(timestamp);
        YearMonth prevMonth = YearMonth.from(currentDate).minusMonths(1);
        LocalDate startOfPrevMonth = prevMonth.atDay(1);
        LocalDate endOfPrevMonth = prevMonth.atEndOfMonth();

        for (DeveloperUser dev : devs) {
            boolean matches = true;

            if (filters.containsKey("expertiseArea")) {
                String val = (String) filters.get("expertiseArea");
                if (!dev.getExpertise().toString().equals(val)) {
                    matches = false;
                }
            }

            if (filters.containsKey("seniority")) {
                String val = (String) filters.get("seniority");
                if (!dev.getSeniority().toString().equals(val)) {
                    matches = false;
                }
            }

            if (filters.containsKey("keywords")) {
                Object value = filters.get("keywords");
                List<?> keywords = value instanceof List<?> list ? list : List.of(value);
                String username = dev.getUsername().toLowerCase(Locale.ROOT);
                String email = dev.getEmail() != null ? dev.getEmail().toLowerCase(Locale.ROOT) :
                        "";
                boolean found = keywords.stream().map(Object::toString)
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .anyMatch(word -> username.contains(word) || email.contains(word));
                if (!found) {
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
                    if (!dev.getHireDate().isAfter(filterDate)) {
                        matches = false;
                    }
                } else {
                    matches = false;
                }
            }

            if (filters.containsKey("createdBefore")) {
                String val = (String) filters.get("createdBefore");
                if (dev.getHireDate() != null) {
                    LocalDate filterDate = LocalDate.parse(val);
                    if (!dev.getHireDate().isBefore(filterDate)) {
                        matches = false;
                    }
                } else {
                    matches = false;
                }
            }

            if (filters.containsKey("performanceScoreAbove")
                    || filters.containsKey("performanceScoreBelow")) {
                double score = dev.getPerformanceScore();

                /* Apply performance filters only if specified in the
                input, to find developers below or above a certain threshold
                 */
                if (filters.containsKey("performanceScoreAbove")) {
                    double limit = ((Number) filters.get("performanceScoreAbove")).doubleValue();
                    if (score < limit) {
                        matches = false;
                    }
                }

                if (filters.containsKey("performanceScoreBelow")) {
                    double limit = ((Number) filters.get("performanceScoreBelow")).doubleValue();
                    if (score >= limit) {
                        matches = false;
                    }
                }
            }

            if (matches) {
                results.add(dev);
            }
        }
        return results;
    }
}
