package portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.TrackerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static portfolio.TestData.*;

class SessionValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "42", "{}",
            "{\"command\":22,\"username\":\"manager\",\"timestamp\":\"2025-10-20\"}",
            "{\"command\":\"viewTickets\",\"username\":\"manager\",\"timestamp\":\"2025-02-29\"}",
            "{\"command\":\"viewTickets\",\"username\":\"manager\",\"timestamp\":\"2025-10-20\",\"params\":[]}",
            "{\"command\":\"viewTickets\",\"username\":\"manager\",\"timestamp\":\"2025-10-20\",\"params\":null}"
    })
    void rejectsMalformedEnvelopesWithoutAbortingSession(String raw) throws Exception {
        TrackerSession session = session();
        assertThat(session.execute(JSON.readTree(raw))).singleElement()
                .satisfies(error -> assertThat(error.has("error")).isTrue());
        prepare(session);
        assertThat(ticket(session).path("id").asInt()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "0.0", "\"0\"", "2147483648", "999999999999999999999999999999", "true", "[]"})
    void invalidIdsDoNotMutateTickets(String id) throws Exception {
        TrackerSession session = session();
        prepare(session);
        JsonNode before = ticket(session);
        ObjectNode request = command("assignTicket", "fullstack_senior", Map.of());
        request.withObject("params").set("ticketID", JSON.readTree(id));
        assertThat(session.execute(request).getFirst().has("error")).isTrue();
        assertThat(ticket(session)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"UNKNOWN\"}", "{\"businessPriority\":\"URGENT\"}",
            "{\"expertiseArea\":\"MOBILE\"}", "{\"severity\":3}", "{\"title\":\" \"}",
            "{\"usabilityScore\":0}", "{\"usabilityScore\":11}", "{\"usabilityScore\":2.5}",
            "{\"errorCode\":2147483648}", "{\"reportedBy\":null}", "{\"frequency\":[]}",
            "{\"description\":{}}"
    })
    void invalidReportsDoNotConsumeIdsOrStartTestingWindow(String patch) throws Exception {
        TrackerSession session = session();
        ObjectNode report = command("reportTicket", "reporter", "2025-10-01", bug());
        ObjectNode params = (ObjectNode) report.get("params");
        params.setAll((ObjectNode) JSON.readTree(patch));
        assertThat(session.execute(report).getFirst().has("error")).isTrue();
        assertThat(session.execute(command("reportTicket", "reporter", bug()))).isEmpty();
        assertThat(ticket(session).path("id").asInt()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"filters\":[]}", "{\"filters\":null}", "{\"filters\":{\"searchType\":\"USER\"}}",
            "{\"filters\":{\"availableForAssignment\":\"true\"}}",
            "{\"filters\":{\"createdBefore\":\"2025-13-01\"}}",
            "{\"filters\":{\"keywords\":[null]}}", "{\"filters\":{\"performanceScoreAbove\":\"10\"}}"
    })
    void malformedFiltersProduceErrors(String params) throws Exception {
        TrackerSession session = session();
        ObjectNode search = command("search", "manager", Map.of());
        search.set("params", JSON.readTree(params));
        assertThat(session.execute(search).getFirst().has("error")).isTrue();
    }

    @Test
    void notificationsAndStateStayInTheirOwnInterleavedSessions() throws Exception {
        TrackerSession first = session();
        TrackerSession second = session();
        prepare(first);
        assertThat(second.execute(command("viewTickets", "manager", Map.of())).getFirst()
                .get("tickets")).isEmpty();
        assertThat(second.execute(command("viewNotifications", "fullstack_senior", Map.of()))
                .getFirst().get("notifications")).isEmpty();
        assertThat(first.execute(command("viewNotifications", "fullstack_senior", Map.of()))
                .getFirst().get("notifications")).hasSize(1);
        assertThat(first.execute(command("viewNotifications", "fullstack_senior", Map.of()))
                .getFirst().get("notifications")).isEmpty();
        prepare(second);
        assertThat(ticket(second).get("id").asInt()).isZero();
    }

    @Test
    void inputAndOutputTreesCannotAliasLiveState() throws Exception {
        ObjectNode catalog = JSON.createObjectNode();
        JsonNode users = JSON.readTree(Path.of("examples/users.json").toFile());
        TrackerSession session = new TrackerSession(users);
        ((ObjectNode) users.get(0)).put("username", "changed");
        prepare(session);
        ObjectNode response = (ObjectNode) ticket(session);
        response.put("status", "CLOSED");
        assertThat(ticket(session).get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void duplicateFlatAndNestedParametersAreRejected() throws Exception {
        TrackerSession session = session();
        prepare(session);
        ObjectNode assign = command("assignTicket", "fullstack_senior", Map.of("ticketID", 0));
        assign.put("ticketID", 1);
        assertThat(session.execute(assign).getFirst().get("error").asText()).contains("twice");
        assertThat(ticket(session).get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void unknownCommandsAreVisibleAndDoNotStopFollowingCommands() throws Exception {
        TrackerSession session = session();
        assertThat(session.execute(command("deleteEverything", "manager", Map.of())).getFirst()
                .get("error").asText()).contains("Unknown command");
        prepare(session);
    }

    @Test
    void unauthorizedPhaseChangesCannotDisableReporting() throws Exception {
        TrackerSession session = session();
        assertThat(session.execute(command("lostInvestors", "reporter", Map.of())).getFirst()
                .has("error")).isTrue();
        assertThat(session.execute(command("lostInvestors", "missing", Map.of())).getFirst()
                .has("error")).isTrue();
        prepare(session);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[null]", "[{\"username\":\"x\",\"role\":\"ADMIN\"}]",
            "[{\"username\":\"x\",\"role\":\"DEVELOPER\"}]",
            "[{\"username\":\"x\",\"role\":\"MANAGER\",\"subordinates\":[\"missing\"]}]",
            "[{\"username\":\"x\",\"role\":\"REPORTER\"},{\"username\":\"x\",\"role\":\"REPORTER\"}]"})
    void invalidCatalogsFailBeforeRunningCommands(String raw) throws Exception {
        JsonNode users = JSON.readTree(raw);
        assertThatThrownBy(() -> new TrackerSession(users)).isInstanceOf(java.io.IOException.class);
    }
    @Test
    void rejectedAnonymousFeatureDoesNotStartTestingWindow() throws Exception {
        TrackerSession session = session();
        ObjectNode invalid = command("reportTicket", "reporter", "2025-10-01", bug());
        invalid.withObject("params").put("type", "FEATURE_REQUEST").put("reportedBy", "");
        assertThat(session.execute(invalid).getFirst().has("error")).isTrue();
        assertThat(session.execute(command("reportTicket", "reporter", bug()))).isEmpty();
        assertThat(ticket(session).get("id").asInt()).isZero();
    }

}
