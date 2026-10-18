package portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import main.Database;
import main.Milestone;
import main.commands.*;
import main.enums.*;
import main.tickets.BugTicket;
import main.tickets.Ticket;
import main.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectnessRegressionTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    Database db;
    List<ObjectNode> outputs;
    Ticket ticket;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database();
        for (User user : mapper.readValue(Path.of("examples/users.json").toFile(), User[].class)) {
            db.addUser(user);
        }
        outputs = new ArrayList<>();
        ticket = new BugTicket.BugBuilder().withId(0).withType("BUG").withTitle("Example")
                .withPriority(Priority.MEDIUM).withStatus(Status.OPEN)
                .withExpertise(Expertise.FRONTEND).withReporter("reporter").build();
        ticket.setCreatedAt("2025-10-01");
        db.addTicket(ticket);
    }

    InputCommand input(String command, String username, String date, String params) throws Exception {
        return mapper.readValue("{\"command\":\"" + command + "\",\"username\":\"" + username
                + "\",\"timestamp\":\"" + date + "\",\"params\":" + params + "}", InputCommand.class);
    }

    void milestone() throws Exception {
        new CreateMilestoneCommand().execute(input("createMilestone", "manager", "2025-10-20",
                """
                {"name":"Release", "dueDate":"2025-11-20", "tickets":[0],
                 "assignedDevs":["frontend_mid", "fullstack_senior"]}
                """), db, outputs);
    }

    @Test
    void escalationRevokesIneligibleAssignmentBeforeStatusChange() throws Exception {
        milestone();
        new AssignTicketCommand().execute(input("assignTicket", "frontend_mid", "2025-10-20",
                "{\"ticketID\":0}"), db, outputs);
        new ChangeStatusCommand().execute(input("changeStatus", "frontend_mid", "2025-11-19",
                "{\"ticketID\":0}"), db, outputs);
        assertThat(ticket.getStatus()).isEqualTo(Status.OPEN);
        assertThat(ticket.getAssignedTo()).isEmpty();
        assertThat(ticket.getSolvedAt()).isEmpty();
        assertThat(outputs).hasSize(1);
        assertThat(ticket.getHistory().getLast().getAction().name()).isEqualTo("REMOVED_FROM_DEV");
    }

    @Test
    void undoResolutionClearsResolutionDate() throws Exception {
        milestone();
        new AssignTicketCommand().execute(input("assignTicket", "fullstack_senior", "2025-10-20",
                "{\"ticketID\":0}"), db, outputs);
        new ChangeStatusCommand().execute(input("changeStatus", "fullstack_senior", "2025-10-21",
                "{\"ticketID\":0}"), db, outputs);
        new UndoChangeStatusCommand().execute(input("undoChangeStatus", "fullstack_senior", "2025-10-22",
                "{\"ticketID\":0}"), db, outputs);
        assertThat(ticket.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(ticket.getSolvedAt()).isEmpty();
    }

    @Test
    void duplicateMilestoneNamesAreRejectedWithoutHistoryMutation() throws Exception {
        milestone();
        new CreateMilestoneCommand().execute(input("createMilestone", "manager", "2025-10-21",
                "{\"name\":\"Release\",\"dueDate\":\"2025-11-21\",\"tickets\":[]}"), db, outputs);
        assertThat(db.getMilestones()).hasSize(1);
        assertThat(ticket.getHistory()).hasSize(1);
        assertThat(outputs).hasSize(1);
    }

    @Test
    void missingDeveloperRejectsWholeMilestone() throws Exception {
        new CreateMilestoneCommand().execute(input("createMilestone", "manager", "2025-10-20",
                "{\"name\":\"Invalid\",\"dueDate\":\"2025-11-20\",\"tickets\":[0],\"assignedDevs\":[\"missing\"]}"), db, outputs);
        assertThat(db.getMilestones()).isEmpty();
        assertThat(ticket.getHistory()).isEmpty();
        assertThat(outputs).hasSize(1);
    }

    @Test
    void duplicateTicketIdsCannotCreateDuplicateMembership() throws Exception {
        new CreateMilestoneCommand().execute(input("createMilestone", "manager", "2025-10-20",
                "{\"name\":\"Invalid\",\"dueDate\":\"2025-11-20\",\"tickets\":[0,0]}"), db, outputs);
        assertThat(db.getMilestones()).isEmpty();
        assertThat(ticket.getHistory()).isEmpty();
    }

    @Test
    void noBlankTicketNotifications() throws Exception {
        milestone();
        new AssignTicketCommand().execute(input("assignTicket", "fullstack_senior", "2025-10-20",
                "{\"ticketID\":0}"), db, outputs);
        new ChangeStatusCommand().execute(input("changeStatus", "fullstack_senior", "2025-10-21",
                "{\"ticketID\":0}"), db, outputs);
        assertThat(db.getUser("reporter").getNotifications()).doesNotContain("");
    }

    @Test
    void returnedMembershipCannotModifyRepository() throws Exception {
        milestone();
        try {
            db.getMilestones().clear();
        } catch (UnsupportedOperationException expected) {
            // Immutable views and defensive copies both preserve repository membership.
        }
        assertThat(db.getMilestones()).hasSize(1);
    }
    @Test
    void completedMilestonesDoNotEmitUnresolvedDeadlineNotifications() throws Exception {
        milestone();
        new AssignTicketCommand().execute(input("assignTicket", "fullstack_senior", "2025-10-20",
                "{\"ticketID\":0}"), db, outputs);
        new ChangeStatusCommand().execute(input("changeStatus", "fullstack_senior", "2025-10-20",
                "{\"ticketID\":0}"), db, outputs);
        new ChangeStatusCommand().execute(input("changeStatus", "fullstack_senior", "2025-10-20",
                "{\"ticketID\":0}"), db, outputs);
        db.getUser("fullstack_senior").drainNotifications();
        db.refreshMilestones(java.time.LocalDate.parse("2025-11-19"));
        assertThat(db.getUser("fullstack_senior").getNotifications()).isEmpty();
    }

}
