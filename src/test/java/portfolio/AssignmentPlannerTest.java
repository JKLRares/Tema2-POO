package portfolio;

import main.Database;
import main.Milestone;
import main.enums.*;
import main.planning.AssignmentPlanner;
import main.tickets.Ticket;
import main.users.DeveloperUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static portfolio.TestData.*;

class AssignmentPlannerTest {
    final AssignmentPlanner planner = new AssignmentPlanner();
    final LocalDate date = LocalDate.parse(DATE);

    Milestone milestone(Database db, List<Ticket> tickets, List<DeveloperUser> developers) {
        Milestone milestone = new Milestone("Release", date, date.plusDays(60));
        milestone.setCreator("manager");
        for (DeveloperUser developer : developers) {
            db.addUser(developer);
            milestone.addDeveloper(developer.getUsername());
        }
        for (Ticket ticket : tickets) {
            db.addTicket(ticket);
            milestone.addTicket(ticket);
        }
        db.addMilestone(milestone);
        return milestone;
    }

    @Test
    void augmentingPathFindsPlanThatFirstFitMisses() {
        Database db = new Database();
        Milestone milestone = milestone(db, List.of(
                ticket(0, TicketType.BUG, Priority.HIGH, Expertise.FRONTEND),
                ticket(1, TicketType.BUG, Priority.LOW, Expertise.BACKEND)), List.of(
                developer("a_fullstack", Seniority.SENIOR, Expertise.FULLSTACK),
                developer("b_frontend", Seniority.SENIOR, Expertise.FRONTEND)));
        AssignmentPlanner.Plan plan = planner.plan(db, milestone, date, 1);
        assertThat(plan.assignments()).containsExactly(
                new AssignmentPlanner.Assignment(0, "b_frontend", Priority.HIGH),
                new AssignmentPlanner.Assignment(1, "a_fullstack", Priority.LOW));
        assertThat(plan.unplanned()).isEmpty();
    }

    @Test
    void readOnlyProjectionUsesDeadlinePriorityAndExplainsIneligibility() {
        Database db = new Database();
        Ticket ticket = ticket(0, TicketType.BUG, Priority.LOW, Expertise.FRONTEND);
        Milestone milestone = milestone(db, List.of(ticket), List.of(
                developer("mid", Seniority.MID, Expertise.FRONTEND)));
        var before = JSON.valueToTree(db.getTickets());
        AssignmentPlanner.Plan plan = planner.plan(db, milestone, milestone.getDueDate().minusDays(1), 1);
        assertThat(plan.assignments()).isEmpty();
        assertThat(plan.unplanned()).containsExactly(
                new AssignmentPlanner.Unplanned(0, "NO_ELIGIBLE_DEVELOPER", List.of()));
        assertThat(JSON.<com.fasterxml.jackson.databind.JsonNode>valueToTree(db.getTickets())).isEqualTo(before);
        assertThat(db.getUser("mid").getNotifications()).isEmpty();
    }

    @Test
    void resolvedWorkElsewhereConsumesCapacityUntilClosed() {
        Database db = new Database();
        Milestone milestone = milestone(db, List.of(ticket(0, TicketType.BUG, Priority.LOW, Expertise.DB)),
                List.of(developer("dev", Seniority.SENIOR, Expertise.FULLSTACK)));
        Ticket busy = ticket(1, TicketType.BUG, Priority.LOW, Expertise.FRONTEND);
        db.addTicket(busy);
        busy.assignTo("dev", DATE);
        busy.advanceStatus("dev", DATE);
        assertThat(planner.plan(db, milestone, date, 1).unplanned().getFirst().reason()).isEqualTo("CAPACITY");
        busy.advanceStatus("dev", DATE);
        assertThat(planner.plan(db, milestone, date, 1).assignments()).hasSize(1);
    }

    @Test
    void capacityBoundariesDoNotRequireAllocatingCapacitySlots() {
        Database db = new Database();
        Milestone milestone = milestone(db, List.of(ticket(0, TicketType.BUG, Priority.LOW, Expertise.DB)),
                List.of(developer("dev", Seniority.SENIOR, Expertise.FULLSTACK)));
        assertThat(planner.plan(db, milestone, date, 0).assignments()).isEmpty();
        assertThat(planner.plan(db, milestone, date, Integer.MAX_VALUE).assignments()).hasSize(1);
        assertThatThrownBy(() -> planner.plan(db, milestone, date, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blockedAndFutureMilestonesHaveNoProposals() {
        Database db = new Database();
        Milestone milestone = milestone(db, List.of(ticket(0, TicketType.BUG, Priority.LOW, Expertise.DB)),
                List.of(developer("dev", Seniority.SENIOR, Expertise.FULLSTACK)));
        assertThat(planner.plan(db, milestone, date.minusDays(1), 1).unplanned().getFirst().reason())
                .isEqualTo("NOT_STARTED");
        Milestone blocker = new Milestone("Blocker", date, date.plusDays(20));
        blocker.addTicket(ticket(1, TicketType.BUG, Priority.LOW, Expertise.DB));
        milestone.addDependency(blocker);
        var plan = planner.plan(db, milestone, date, 1);
        assertThat(plan.blocked()).isTrue();
        assertThat(plan.assignments()).isEmpty();
        assertThat(plan.unplanned().getFirst().reason()).isEqualTo("BLOCKED");
    }

    @Test
    void generatedPlansMatchIndependentExhaustiveOracle() {
        Random random = new Random(0xAC52025);
        for (int scenario = 0; scenario < 300; scenario++) {
            Database db = new Database();
            int n = random.nextInt(7) + 1;
            int d = random.nextInt(4) + 1;
            int capacity = random.nextInt(4);
            List<Ticket> tickets = new ArrayList<>();
            List<DeveloperUser> developers = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                tickets.add(ticket(i, TicketType.values()[random.nextInt(3)],
                        Priority.values()[random.nextInt(4)], Expertise.values()[random.nextInt(6)]));
            }
            for (int i = 0; i < d; i++) {
                developers.add(developer("dev" + i, Seniority.values()[random.nextInt(3)],
                        Expertise.values()[random.nextInt(6)]));
            }
            Milestone milestone = milestone(db, tickets, developers);
            List<Ticket> ordered = tickets.stream().sorted(Comparator.comparing(Ticket::getPriority)
                    .reversed().thenComparing(Ticket::getId)).toList();
            int[] best = { -1, -1 };
            exhaustive(ordered, developers, new int[d], capacity, 0, 0, 0, best);
            var snapshot = JSON.valueToTree(db.getTickets());
            var plan = planner.plan(db, milestone, date, capacity);
            assertThat(plan.assignments()).as("scenario %s", scenario).hasSize(best[0]);
            Set<Integer> assigned = new HashSet<>();
            Map<String, Integer> loads = new HashMap<>();
            int mask = 0;
            for (var proposal : plan.assignments()) {
                assertThat(assigned.add(proposal.ticketID())).isTrue();
                Ticket ticket = db.getTicket(proposal.ticketID());
                DeveloperUser developer = (DeveloperUser) db.getUser(proposal.developer());
                assertThat(eligible(developer, ticket)).isTrue();
                loads.merge(proposal.developer(), 1, Integer::sum);
                mask |= 1 << (n - 1 - ordered.indexOf(ticket));
            }
            assertThat(loads.values()).allMatch(load -> load <= capacity);
            assertThat(mask).as("priority selection in scenario %s", scenario).isEqualTo(best[1]);
            assertThat(planner.plan(db, milestone, date, capacity)).isEqualTo(plan);
            assertThat(JSON.<com.fasterxml.jackson.databind.JsonNode>valueToTree(db.getTickets())).isEqualTo(snapshot);
            assertThat(plan.assignments().size() + plan.unplanned().size()).isEqualTo(n);
        }
    }

    void exhaustive(List<Ticket> tickets, List<DeveloperUser> developers, int[] loads, int capacity,
                    int index, int count, int mask, int[] best) {
        if (index == tickets.size()) {
            if (count > best[0] || count == best[0] && mask > best[1]) {
                best[0] = count;
                best[1] = mask;
            }
            return;
        }
        exhaustive(tickets, developers, loads, capacity, index + 1, count, mask, best);
        for (int i = 0; i < developers.size(); i++) {
            if (loads[i] < capacity && eligible(developers.get(i), tickets.get(index))) {
                loads[i]++;
                exhaustive(tickets, developers, loads, capacity, index + 1, count + 1,
                        mask | 1 << (tickets.size() - 1 - index), best);
                loads[i]--;
            }
        }
    }

    // Independent policy oracle: do not call the production assignment policy here.
    boolean eligible(DeveloperUser developer, Ticket ticket) {
        if (developer.getSeniority() == Seniority.JUNIOR
                && (ticket.getPriority().ordinal() > 1 || ticket.getType() == TicketType.FEATURE_REQUEST)) return false;
        if (developer.getSeniority() == Seniority.MID && ticket.getPriority() == Priority.CRITICAL) return false;
        Expertise own = developer.getExpertise();
        Expertise area = ticket.getCategory();
        return own == Expertise.FULLSTACK || own == area
                || own == Expertise.FRONTEND && area == Expertise.DESIGN
                || own == Expertise.DESIGN && area == Expertise.FRONTEND
                || own == Expertise.BACKEND && area == Expertise.DB;
    }
}
