package portfolio;

import main.Database;
import main.Milestone;
import main.TrackerSession;
import main.enums.*;
import main.tickets.Ticket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static portfolio.TestData.*;

class LifecycleInvariantTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19})
    void generatedCommandSequencesMatchIndependentStateMachine(int seed) throws Exception {
        TrackerSession session = session();
        prepare(session);
        Random random = new Random(seed);
        Status expected = Status.OPEN;
        String owner = "";
        int expectedHistory = 1;
        String[] operations = {"assignTicket", "changeStatus", "undoChangeStatus", "undoAssignTicket"};
        for (int step = 0; step < 150; step++) {
            String operation = operations[random.nextInt(operations.length)];
            String actor = random.nextInt(5) == 0 ? "frontend_mid" : "fullstack_senior";
            session.execute(command(operation, actor, Map.of("ticketID", 0)));
            if (operation.equals("assignTicket") && expected == Status.OPEN) {
                expected = Status.IN_PROGRESS;
                owner = actor;
                expectedHistory += 2;
            } else if (actor.equals(owner)) {
                if (operation.equals("changeStatus") && expected == Status.IN_PROGRESS) {
                    expected = Status.RESOLVED;
                    expectedHistory++;
                } else if (operation.equals("changeStatus") && expected == Status.RESOLVED) {
                    expected = Status.CLOSED;
                    expectedHistory++;
                } else if (operation.equals("undoChangeStatus") && expected == Status.CLOSED) {
                    expected = Status.RESOLVED;
                    expectedHistory++;
                } else if (operation.equals("undoChangeStatus") && expected == Status.RESOLVED) {
                    expected = Status.IN_PROGRESS;
                    expectedHistory++;
                } else if (operation.equals("undoAssignTicket") && expected == Status.IN_PROGRESS) {
                    expected = Status.OPEN;
                    owner = "";
                    expectedHistory++;
                }
            }
            var actual = ticket(session);
            assertThat(actual.get("status").asText()).as("seed %s step %s", seed, step).isEqualTo(expected.name());
            assertThat(actual.get("assignedTo").asText()).isEqualTo(owner);
            assertThat(actual.get("assignedAt").asText()).isEqualTo(expected == Status.OPEN ? "" : DATE);
            boolean solved = expected == Status.RESOLVED || expected == Status.CLOSED;
            assertThat(actual.get("solvedAt").asText()).isEqualTo(solved ? DATE : "");
            var history = session.execute(command("viewTicketHistory", "manager", Map.of()))
                    .getFirst().get("ticketHistory").get(0).get("actions");
            assertThat(history).hasSize(expectedHistory);
        }
    }

    @Test
    void closingAndUndoingPreserveResolutionDateAndSeparateClosureDate() {
        Ticket ticket = ticket(0, TicketType.BUG, Priority.LOW, Expertise.FRONTEND);
        ticket.assignTo("dev", "2025-10-20");
        ticket.advanceStatus("dev", "2025-10-21");
        ticket.advanceStatus("dev", "2025-10-25");
        assertThat(ticket.getSolvedAt()).isEqualTo("2025-10-21");
        assertThat(ticket.getClosedAt()).isEqualTo("2025-10-25");
        ticket.undoStatus("dev", "2025-10-26");
        assertThat(ticket.getStatus()).isEqualTo(Status.RESOLVED);
        assertThat(ticket.getSolvedAt()).isEqualTo("2025-10-21");
        assertThat(ticket.getClosedAt()).isEmpty();
        ticket.undoStatus("dev", "2025-10-27");
        ticket.releaseAssignment("2025-10-28", HistoryActionType.DE_ASSIGNED);
        ticket.assignTo("other", "2025-10-29");
        assertThat(ticket.getSolvedAt()).isEmpty();
        assertThat(ticket.getResolutionDate()).isEmpty();
    }

    @Test
    void hugeDateRangesSaturatePriorityWithoutIntegerOverflow() {
        Database db = new Database();
        Ticket ticket = ticket(0, TicketType.BUG, Priority.LOW, Expertise.FRONTEND);
        db.addTicket(ticket);
        Milestone milestone = new Milestone("Ancient", LocalDate.MIN, LocalDate.MAX);
        milestone.addTicket(ticket);
        milestone.setCreator("manager");
        db.addMilestone(milestone);
        db.refreshMilestone(milestone, LocalDate.of(0, 1, 1));
        assertThat(ticket.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(milestone.priorityAt(ticket, LocalDate.of(0, 1, 1))).isEqualTo(Priority.CRITICAL);
        db.refreshMilestone(milestone, LocalDate.MIN);
        assertThat(ticket.getPriority()).isEqualTo(Priority.CRITICAL);
    }

    @Test
    void registeredMilestoneMembershipAndIdentityAreFixed() {
        Database db = new Database();
        Ticket ticket = ticket(0, TicketType.BUG, Priority.LOW, Expertise.FRONTEND);
        db.addTicket(ticket);
        Milestone milestone = new Milestone("Release", LocalDate.parse(DATE), LocalDate.parse(DATE).plusDays(20));
        milestone.addTicket(ticket);
        milestone.setCreator("manager");
        db.addMilestone(milestone);
        assertThatThrownBy(() -> milestone.addTicket(ticket)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> milestone.setCreator("other")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> milestone.getTickets().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(db.getMilestoneForTicket(0)).isSameAs(milestone);
    }

    @Test
    void malformedMilestoneCannotPartiallyChangeExistingGraphOrHistory() throws Exception {
        TrackerSession session = session();
        prepare(session);
        var before = session.execute(command("viewMilestones", "manager", Map.of())).getFirst();
        var result = session.execute(command("createMilestone", "manager", Map.of(
                "name", "Rejected", "dueDate", "2025-12-20", "tickets", new int[]{},
                "blockingFor", new String[]{"Release", "Missing"})));
        assertThat(result.getFirst().has("error")).isTrue();
        assertThat(session.execute(command("viewMilestones", "manager", Map.of())).getFirst()).isEqualTo(before);
    }

    @Test
    void developerKeywordArraysAreSupportedWithoutClassCast() throws Exception {
        TrackerSession session = session();
        var result = session.execute(command("search", "manager", Map.of("filters", Map.of(
                "searchType", "DEVELOPER", "keywords", new String[]{"frontend", "backend"}))));
        assertThat(result.getFirst().get("results")).hasSize(2);
    }

    @Test
    void planningCommandEnforcesOwnershipAndRemainsReadOnly() throws Exception {
        TrackerSession session = session();
        prepare(session);
        var before = ticket(session);
        var planCommand = command("planAssignments", "manager", Map.of("milestone", "Release"));
        var first = session.execute(planCommand);
        assertThat(first.getFirst().get("plan").get("assignments")).hasSize(1);
        assertThat(session.execute(planCommand)).isEqualTo(first);
        assertThat(ticket(session)).isEqualTo(before);
        assertThat(session.execute(command("planAssignments", "reporter", Map.of("milestone", "Release")))
                .getFirst().has("error")).isTrue();
        assertThat(session.execute(command("planAssignments", "manager", Map.of("milestone", "missing")))
                .getFirst().has("error")).isTrue();
    }
    @Test
    void lateUnblockingIsNotDependentOnReadingWhileBlocked() throws Exception {
        TrackerSession session = session();
        session.execute(command("reportTicket", "reporter", bug()));
        session.execute(command("reportTicket", "reporter", bug()));
        session.execute(command("createMilestone", "manager", Map.of("name", "Target",
                "dueDate", "2025-10-22", "tickets", new int[]{0},
                "assignedDevs", new String[]{"fullstack_senior"})));
        session.execute(command("createMilestone", "manager", Map.of("name", "Blocker",
                "dueDate", "2025-10-30", "tickets", new int[]{1},
                "assignedDevs", new String[]{"fullstack_senior"}, "blockingFor", new String[]{"Target"})));
        session.execute(command("assignTicket", "fullstack_senior", Map.of("ticketID", 1)));
        session.execute(command("changeStatus", "fullstack_senior", "2025-10-25", Map.of("ticketID", 1)));
        session.execute(command("changeStatus", "fullstack_senior", "2025-10-25", Map.of("ticketID", 1)));
        var notifications = session.execute(command("viewNotifications", "fullstack_senior",
                "2025-10-25", Map.of())).getFirst().get("notifications");
        assertThat(notifications).anyMatch(message -> message.asText().contains("was unblocked after due date"));
        assertThat(session.execute(command("viewNotifications", "fullstack_senior", "2025-10-25", Map.of()))
                .getFirst().get("notifications")).isEmpty();
    }

}
