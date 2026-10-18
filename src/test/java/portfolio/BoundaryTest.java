package portfolio;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.TrackerSession;
import main.enums.Expertise;
import main.enums.Priority;
import main.enums.TicketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static portfolio.TestData.*;

class BoundaryTest {
    @ParameterizedTest
    @EnumSource(TicketType.class)
    void resolutionMetricsRejectNonpositiveDurations(TicketType type) {
        var ticket = ticket(0, type, Priority.LOW, Expertise.FRONTEND);
        assertThatThrownBy(() -> ticket.calculateEfficiency(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ticket.calculateEfficiency(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Double.isFinite(ticket.calculateEfficiency(1))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-999999999-01-01", "+999999999-12-31", "+10000-01-01"})
    void protocolDatesRequireFourDigitYears(String date) throws Exception {
        TrackerSession session = session();
        var result = session.execute(command("search", "manager", date,
                Map.of("filters", Map.of("searchType", "DEVELOPER"))));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().has("error")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "manager", "fullstack_senior"})
    void invalidReporterReferencesDoNotConsumeIdsOrStartTestingWindow(String reporter) throws Exception {
        TrackerSession session = session();
        ObjectNode invalid = command("reportTicket", "reporter", "2025-10-01", bug());
        invalid.withObject("params").put("reportedBy", reporter);
        assertThat(session.execute(invalid).getFirst().has("error")).isTrue();
        assertThat(session.execute(command("reportTicket", "reporter", bug()))).isEmpty();
        assertThat(ticket(session).get("id").asInt()).isZero();
    }

    @Test
    void aDifferentManagerCannotPlanAnotherManagersMilestone() throws Exception {
        ArrayNode catalog = (ArrayNode) JSON.readTree(Path.of("examples/users.json").toFile());
        catalog.add(JSON.valueToTree(Map.of("username", "other", "role", "MANAGER")));
        TrackerSession session = new TrackerSession(catalog);
        prepare(session);
        assertThat(session.execute(command("planAssignments", "other", Map.of("milestone", "Release")))
                .getFirst().get("error").asText()).contains("not owned");
    }

    @Test
    void duplicateRepositoryIdsNeverOverwriteEntitiesOrAdvanceAllocator() {
        Database db = new Database();
        var original = ticket(0, TicketType.BUG, Priority.LOW, Expertise.FRONTEND);
        db.addTicket(original);
        assertThatThrownBy(() -> db.addTicket(ticket(0, TicketType.BUG, Priority.HIGH, Expertise.BACKEND)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(db.getTicket(0)).isSameAs(original);
        assertThat(db.getNextTicketId()).isEqualTo(1);
    }
    @Test
    void maximumTicketIdDoesNotOverflowAllocator() {
        Database db = new Database();
        db.addTicket(ticket(Integer.MAX_VALUE, TicketType.BUG, Priority.LOW, Expertise.FRONTEND));
        assertThat(db.getTicket(Integer.MAX_VALUE)).isNotNull();
        assertThatThrownBy(db::getNextTicketId).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted");
    }

}
