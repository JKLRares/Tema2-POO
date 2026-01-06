package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class ReportTicketCommand implements Command {

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

        if (user.getRole() != UserRole.REPORTER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " is not a reporter.");
            outputs.add(error);
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.timestamp);

        if (db.isTestingPhase() && db.getTestingPhaseStartDate() == null) {
            db.setTestingPhaseStartDate(currentDate);
        }

        if (db.isTestingPhase() && db.getTestingPhaseStartDate() != null) {
            long daysPassed = ChronoUnit.DAYS.between(db.getTestingPhaseStartDate(), currentDate) + 1;

            if (daysPassed > 12) {
                db.setTestingPhase(false);
                db.setTestingPhaseStartDate(null);
            }
        }

        if (!db.isTestingPhase()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Tickets can only be reported during testing phases.");
            outputs.add(error);
            return;
        }

        Map<String, Object> p = input.params;
        String typeStr = (String) p.get("type");
        String reportedBy = (String) p.get("reportedBy");

        if (reportedBy == null) reportedBy = "";

        boolean isAnonymous = reportedBy.trim().isEmpty();
        if (isAnonymous && !"BUG".equals(typeStr)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Anonymous reports are only allowed for tickets of type BUG.");
            outputs.add(error);
            return;
        }

        Ticket ticket = null;
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

        if ("BUG".equals(typeStr)) {
            BugTicket.Type1Builder builder = new BugTicket.Type1Builder();

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

            if (p.get("frequency") != null)
                builder.setFrequency(Frequency.valueOf((String) p.get("frequency")));
            if (p.get("severity") != null)
                builder.setSeverity(Severity.valueOf((String) p.get("severity")));

            if (p.get("errorCode") != null)
                builder.setErrorCode((Integer) p.get("errorCode"));

            ticket = builder.build();

        } else if ("FEATURE_REQUEST".equals(typeStr)) {
            FeatureRequestTicket.Type2Builder builder = new FeatureRequestTicket.Type2Builder();

            builder.withId(id)
                    .withType(typeStr)
                    .withTitle(title)
                    .withDescription(description)
                    .withPriority(priority)
                    .withStatus(Status.OPEN)
                    .withReporter(reportedBy);

            if (p.get("businessValue") != null)
                builder.setBusinessValue(BusinessValue.valueOf((String) p.get("businessValue")));
            if (p.get("customerDemand") != null)
                builder.setCustomerDemand(CustomerDemand.valueOf((String) p.get("customerDemand")));

            ticket = builder.build();

        } else if ("UI_FEEDBACK".equals(typeStr)) {
            UiFeedbackTicket.Type3Builder builder = new UiFeedbackTicket.Type3Builder();

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

            if (p.get("businessValue") != null)
                builder.setBusinessValue(BusinessValue.valueOf((String) p.get("businessValue")));
            if (p.get("usabilityScore") != null)
                builder.setUsabilityScore((Integer) p.get("usabilityScore"));

            ticket = builder.build();
        }

        if (ticket != null) {
            ticket.setCreatedAt(input.timestamp);

            if (expertise != null) {
                ticket.setExpertiseArea(expertise);
            }

            db.addTicket(ticket);
        }
    }
}