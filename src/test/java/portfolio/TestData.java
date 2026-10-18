package portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.JsonSupport;
import main.TrackerSession;
import main.enums.*;
import main.tickets.*;
import main.users.DeveloperUser;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class TestData {
    static final ObjectMapper JSON = JsonSupport.mapper();
    static final String DATE = "2025-10-20";

    static TrackerSession session() throws Exception {
        return new TrackerSession(JSON.readTree(Path.of("examples/users.json").toFile()));
    }

    static ObjectNode command(String name, String user, String date, Map<String, ?> params) {
        ObjectNode command = JSON.createObjectNode();
        command.put("command", name).put("username", user).put("timestamp", date);
        command.set("params", JSON.valueToTree(params));
        return command;
    }

    static ObjectNode command(String name, String user, Map<String, ?> params) {
        return command(name, user, DATE, params);
    }

    static Map<String, Object> bug() {
        return Map.of("type", "BUG", "title", "Save button crash", "reportedBy", "reporter",
                "businessPriority", "MEDIUM", "expertiseArea", "FRONTEND",
                "frequency", "OCCASIONAL", "severity", "MODERATE");
    }

    static void prepare(TrackerSession session) {
        assertThat(session.execute(command("reportTicket", "reporter", bug()))).isEmpty();
        assertThat(session.execute(command("createMilestone", "manager", Map.of(
                "name", "Release", "dueDate", "2025-12-20", "tickets", new int[]{0},
                "assignedDevs", new String[]{"fullstack_senior", "frontend_mid"})))).isEmpty();
    }

    static JsonNode ticket(TrackerSession session) {
        return session.execute(command("viewTickets", "manager", Map.of())).getFirst()
                .get("tickets").get(0);
    }

    static Ticket ticket(int id, TicketType type, Priority priority, Expertise expertise) {
        Ticket ticket = switch (type) {
            case BUG -> new BugTicket.BugBuilder().withId(id).withType(type.name())
                    .withTitle("Ticket " + id).withPriority(priority).withExpertise(expertise)
                    .withStatus(Status.OPEN).withReporter("reporter").build();
            case FEATURE_REQUEST -> new FeatureRequestTicket.FeatureReqBuilder().withId(id)
                    .withType(type.name()).withTitle("Ticket " + id).withPriority(priority)
                    .withExpertise(expertise).withStatus(Status.OPEN).withReporter("reporter").build();
            case UI_FEEDBACK -> new UiFeedbackTicket.UiFeedBuilder().withId(id).withType(type.name())
                    .withTitle("Ticket " + id).withPriority(priority).withExpertise(expertise)
                    .withStatus(Status.OPEN).withReporter("reporter").build();
        };
        ticket.setCreatedAt(DATE);
        return ticket;
    }

    static DeveloperUser developer(String name, Seniority seniority, Expertise expertise) {
        return JSON.convertValue(Map.of("username", name, "role", "DEVELOPER",
                "seniority", seniority.name(), "expertiseArea", expertise.name()), DeveloperUser.class);
    }
}
