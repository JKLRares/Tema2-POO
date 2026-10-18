package main.commands;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import main.enums.BusinessValue;
import main.enums.CustomerDemand;
import main.enums.Expertise;
import main.enums.Frequency;
import main.enums.Priority;
import main.enums.Seniority;
import main.enums.Severity;
import main.enums.TicketType;

/** Validates untyped JSON at the boundary before a handler can mutate session state. */
public final class CommandValidator {
    private static final int MAX_USABILITY = 10;
    private CommandValidator() {
    }

    /** Rejects missing identities, invalid dates and non-object parameters. */
    public static void validateEnvelope(final InputCommand input) {
        if (input == null) {
            fail("Command must be an object.");
        }
        text(input.getCommand(), "command", true);
        text(input.getUsername(), "username", true);
        date(input.getTimestamp(), "timestamp");
        if (input.getParams() == null) {
            fail("params must be an object.");
        }
    }

    /** Checks command-specific types and ranges before any handler mutation. */
    public static void validateParameters(final InputCommand input) {
        Map<String, Object> p = input.getParams();
        switch (input.getCommand()) {
            case "reportTicket":
                enumeration(p.get("type"), "type", TicketType.class);
                text(p.get("title"), "title", true);
                enumeration(p.get("businessPriority"), "businessPriority", Priority.class);
                optionalEnum(p, "expertiseArea", Expertise.class);
                optionalEnum(p, "frequency", Frequency.class);
                optionalEnum(p, "severity", Severity.class);
                optionalEnum(p, "businessValue", BusinessValue.class);
                optionalEnum(p, "customerDemand", CustomerDemand.class);
                for (String field : List.of("reportedBy", "description", "expectedBehavior",
                        "actualBehavior", "environment", "uiElementId", "screenshotUrl",
                                "suggestedFix")) {
                    optionalText(p, field);
                }
                if (p.containsKey("errorCode")) {
                    integer(p.get("errorCode"), "errorCode", Integer.MIN_VALUE);
                }
                if (p.containsKey("usabilityScore")) {
                    int score = integer(p.get("usabilityScore"), "usabilityScore", 1);
                    if (score > MAX_USABILITY) {
                        fail("usabilityScore must be between 1 and 10.");
                    }
                }
                break;
            case "createMilestone":
                text(p.get("name"), "name", true);
                date(p.get("dueDate"), "dueDate");
                if (p.containsKey("startDate")) {
                    date(p.get("startDate"), "startDate");
                }
                list(p, "tickets", true);
                list(p, "assignedDevs", false);
                list(p, "blockingFor", false);
                break;
            case "assignTicket", "undoAssignTicket", "changeStatus", "undoChangeStatus",
                    "addComment", "undoAddComment":
                integer(p.get("ticketID"), "ticketID", 0);
                optionalText(p, "comment");
                break;
            case "search":
                if (p.containsKey("filters")) {
                    filters(p.get("filters"));
                }
                break;
            case "planAssignments":
                text(p.get("milestone"), "milestone", true);
                if (p.containsKey("capacityPerDeveloper")) {
                    integer(p.get("capacityPerDeveloper"), "capacityPerDeveloper", 0);
                }
                break;
            default:
                break;
        }
    }

    private static void filters(final Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            fail("filters must be an object.");
            return;
        }
        Object kind = map.get("searchType");
        if (map.containsKey("searchType") && !"TICKET".equals(kind) && !"DEVELOPER".equals(kind)) {
            fail("searchType must be TICKET or DEVELOPER.");
        }
        for (String field : List.of("createdAt", "createdAfter", "createdBefore")) {
            if (map.containsKey(field)) {
                date(map.get(field), field);
            }
        }
        optionalEnum(map, "type", TicketType.class);
        optionalEnum(map, "businessPriority", Priority.class);
        optionalEnum(map, "expertiseArea", Expertise.class);
        optionalEnum(map, "seniority", Seniority.class);
        if (map.containsKey("availableForAssignment")
                && !(map.get("availableForAssignment") instanceof Boolean)) {
            fail("availableForAssignment must be a boolean.");
        }
        for (String field : List.of("performanceScoreAbove", "performanceScoreBelow")) {
            if (map.containsKey(field) && (!(map.get(field) instanceof Number number)
                    || !Double.isFinite(number.doubleValue()))) {
                fail(field + " must be a finite number.");
            }
        }
        if (map.containsKey("keywords")) {
            Object keywords = map.get("keywords");
            if (keywords instanceof List<?> words) {
                for (Object word : words) {
                    text(word, "keywords", true);
                }
            } else {
                text(keywords, "keywords", true);
            }
        }
    }

    private static void list(final Map<String, Object> params, final String field,
                             final boolean ids) {
        if (!params.containsKey(field)) {
            return;
        }
        if (!(params.get(field) instanceof List<?> values)) {
            fail(field + " must be an array.");
            return;
        }
        for (Object value : values) {
            if (ids) {
                integer(value, field, 0);
            } else {
                text(value, field, true);
            }
        }
    }

    private static void optionalText(final Map<String, Object> params, final String field) {
        if (params.containsKey(field)) {
            text(params.get(field), field, false);
        }
    }

    /** Requires a string and optionally rejects blank content. */
    public static String text(final Object value, final String field, final boolean nonblank) {
        if (!(value instanceof String result) || nonblank && result.isBlank()) {
            fail(field + " must be " + (nonblank ? "a nonblank string." : "a string."));
            return null;
        }
        return result;
    }

    /** Parses a required ISO date or reports an expected validation failure. */
    public static LocalDate date(final Object value, final String field) {
        String string = text(value, field, true);
        if (!string.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            fail(field + " must use yyyy-MM-dd with a four-digit year.");
        }
        try {
            return LocalDate.parse(string);
        } catch (DateTimeParseException exception) {
            fail(field + " must be an ISO date (yyyy-MM-dd).");
            return null;
        }
    }

    private static int integer(final Object value, final String field, final int minimum) {
        if (!(value instanceof Integer number) || number < minimum) {
            fail(field + " must be a 32-bit integer greater than or equal to " + minimum + ".");
            return 0;
        }
        return number;
    }

    private static <E extends Enum<E>> void optionalEnum(final Map<?, ?> params,
                                                        final String field, final Class<E> type) {
        if (params.containsKey(field)) {
            enumeration(params.get(field), field, type);
        }
    }

    private static <E extends Enum<E>> void enumeration(final Object value, final String field,
                                                       final Class<E> type) {
        String string = text(value, field, true);
        try {
            Enum.valueOf(type, string);
        } catch (IllegalArgumentException exception) {
            fail("Unsupported " + field + ": " + string + ".");
        }
    }

    private static void fail(final String message) {
        throw new InvalidCommandException(message);
    }
}
