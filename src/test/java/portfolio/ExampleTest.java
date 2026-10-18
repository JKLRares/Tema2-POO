package portfolio;

import main.App;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolio.TestData.JSON;

class ExampleTest {
    @TempDir Path directory;

    @Test
    void documentedExampleProducesAPlanAndAppliesExplicitAssignments() throws Exception {
        Path output = directory.resolve("example.json");
        App.run(Path.of("examples/users.json"), Path.of("examples/commands.json"), output, false);
        var responses = JSON.readTree(output.toFile());
        assertThat(responses).hasSize(2);
        for (var response : responses) assertThat(response.has("error")).isFalse();
        var plan = responses.get(0).get("plan");
        assertThat(plan.get("assignments")).hasSize(2);
        assertThat(plan.get("assignments").get(0).get("developer").asText()).isEqualTo("fullstack_senior");
        assertThat(plan.get("assignments").get(1).get("developer").asText()).isEqualTo("frontend_mid");
        assertThat(plan.get("unplanned").get(0).get("ticketID").asInt()).isEqualTo(2);
        assertThat(plan.get("unplanned").get(0).get("reason").asText()).isEqualTo("CAPACITY");
        var tickets = responses.get(1).get("tickets");
        assertThat(tickets.get(0).get("status").asText()).isEqualTo("CLOSED");
        assertThat(tickets.get(0).get("solvedAt").asText()).isEqualTo("2025-10-21");
        assertThat(tickets.get(1).get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(tickets.get(2).get("status").asText()).isEqualTo("OPEN");
    }
}
