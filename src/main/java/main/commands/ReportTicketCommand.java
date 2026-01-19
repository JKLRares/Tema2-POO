package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import main.Database;
import main.enums.BusinessValue;
import main.enums.CustomerDemand;
import main.enums.Expertise;
import main.enums.Frequency;
import main.enums.Priority;
import main.enums.Severity;
import main.enums.Status;
import main.enums.UserRole;
import main.tickets.BugTicket;
import main.tickets.FeatureRequestTicket;
import main.tickets.Ticket;
import main.tickets.UiFeedbackTicket;
import main.users.User;

public final class ReportTicketCommand implements Command {

    private static final int TWELVE = 12;
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

        if (user.getRole() != UserRole.REPORTER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + input.getUsername() + " is not a reporter.");
            outputs.add(error);
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.getTimestamp());

        if (db.isTestingPhase() && db.getTestingPhaseStartDate() == null) {
            db.setTestingPhaseStartDate(currentDate);
        }

        if (db.isTestingPhase() && db.getTestingPhaseStartDate() != null) {
            long daysPassed = ChronoUnit.DAYS.between(db.getTestingPhaseStartDate(),
                    currentDate) + 1;

            if (daysPassed > TWELVE) {
                db.setTestingPhase(false);
                db.setTestingPhaseStartDate(null);
            }
        }

        /* Tickets can only be reported if the testing phase
        is active and has not expired (max 12 days)
         */
        if (!db.isTestingPhase()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Tickets can only be reported during testing phases.");
            outputs.add(error);
            return;
        }

        Map<String, Object> p = input.getParams();
        String typeStr = (String) p.get("type");
        String reportedBy = (String) p.get("reportedBy");

        if (reportedBy == null) {
            reportedBy = "";
        }

        boolean isAnonymous = reportedBy.trim().isEmpty();
        // Anonymous reporting is allowed exclusively for BUG tickets, not for feature requests
        if (isAnonymous && !"BUG".equals(typeStr)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Anonymous reports are only allowed for tickets of type BUG.");
            outputs.add(error);
            return;
        }

        Ticket ticket;
        Integer id = db.getNextIdAndIncrement();
        String title = (String) p.get("title");
        String description = (String) p.get("description");

        Priority priority = Priority.valueOf((String) p.get("businessPriority"));

        if (isAnonymous) {
            priority = Priority.LOW;
        }

        Expertise expertise = null;
        if (p.get("expertiseArea") != null) {
            expertise = Expertise.valueOf((String) p.get("expertiseArea"));
        }

        ticket = buildTicket(typeStr, id, title, description, priority, reportedBy, p);

        if (ticket != null) {
            ticket.setCreatedAt(input.getTimestamp());

            if (expertise != null) {
                ticket.setExpertiseArea(expertise);
            }

            db.addTicket(ticket);
        }
    }

    private Ticket buildTicket(final String typeStr, final Integer id, final String title,
                               final String description, final Priority priority,
                               final String reportedBy, final Map<String, Object> p) {
        if ("BUG".equals(typeStr)) {
            BugTicket.BugBuilder builder = new BugTicket.BugBuilder();

            builder.withId(id)
                    .withType(typeStr)
                    .withTitle(title)
                    .withDescription(description)
                    .withPriority(priority)
                    .withStatus(Status.OPEN)
                    .withReporter(reportedBy);

            builder.setExpected((String) p.get("expectedBehavior"))
                    .setActual((String) p.get("actualBehavior"))
                    .setEnvironment((String) p.get("environment"));

            if (p.get("frequency") != null) {
                builder.setFrequency(Frequency.valueOf((String) p.get("frequency")));
            }
            if (p.get("severity") != null) {
                builder.setSeverity(Severity.valueOf((String) p.get("severity")));
            }

            if (p.get("errorCode") != null) {
                builder.setErrorCode((Integer) p.get("errorCode"));
            }

            return builder.build();

        } else if ("FEATURE_REQUEST".equals(typeStr)) {
            FeatureRequestTicket.FeatureReqBuilder builder =
                    new FeatureRequestTicket.FeatureReqBuilder();

            builder.withId(id)
                    .withType(typeStr)
                    .withTitle(title)
                    .withDescription(description)
                    .withPriority(priority)
                    .withStatus(Status.OPEN)
                    .withReporter(reportedBy);

            if (p.get("businessValue") != null) {
                builder.setBusinessValue(BusinessValue.valueOf((String) p.get("businessValue")));
            }
            if (p.get("customerDemand") != null) {
                builder.setCustomerDemand(CustomerDemand.valueOf((String) p.get("customerDemand")));
            }

            return builder.build();

        } else if ("UI_FEEDBACK".equals(typeStr)) {
            UiFeedbackTicket.UiFeedBuilder builder = new UiFeedbackTicket.UiFeedBuilder();

            builder.withId(id)
                    .withType(typeStr)
                    .withTitle(title)
                    .withDescription(description)
                    .withPriority(priority)
                    .withStatus(Status.OPEN)
                    .withReporter(reportedBy);

            builder.setUiElementId((String) p.get("uiElementId"))
                    .setScreenshotUrl((String) p.get("screenshotUrl"))
                    .setSuggestedFix((String) p.get("suggestedFix"));

            if (p.get("businessValue") != null) {
                builder.setBusinessValue(BusinessValue.valueOf((String) p.get("businessValue")));
            }
            if (p.get("usabilityScore") != null) {
                builder.setUsabilityScore((Integer) p.get("usabilityScore"));
            }

            return builder.build();
        }
        return null;
    }
}
