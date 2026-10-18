package main.commands;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.Milestone;
import main.enums.HistoryActionType;
import main.enums.UserRole;
import main.tickets.Ticket;
import main.tickets.TicketHistoryEntry;
import main.users.User;

/** Validates all references before publishing a milestone or changing any ticket history. */
public final class CreateMilestoneCommand implements Command {

    @Override
    @SuppressWarnings("unchecked")
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        User user = db.getUser(input.getUsername());
        if (user == null) {
            CommandErrors.add(input, outputs, "The user " + input.getUsername() +
                    " does not exist.");
            return;
        }

        if (user.getRole() != UserRole.MANAGER) {
            CommandErrors.add(input, outputs,
                    "The user does not have permission to execute this command: "
                    + "required role MANAGER; user role " + user.getRole() + ".");
            return;
        }

        Map<String, Object> params = input.getParams();
        String name = (String) params.get("name");
        if (name == null || name.isBlank() || db.getMilestone(name) != null) {
            CommandErrors.add(input, outputs, "Milestone name must be nonblank and unique.");
            return;
        }
        List<Integer> ids = (List<Integer>) params.getOrDefault("tickets", List.of());
        if (new HashSet<>(ids).size() != ids.size()) {
            CommandErrors.add(input, outputs, "Milestone ticket IDs must be unique.");
            return;
        }
        List<Ticket> tickets = new ArrayList<>();
        for (Integer id : ids) {
            Ticket ticket = db.getTicket(id);
            if (ticket == null) {
                CommandErrors.add(input, outputs, "Ticket ID " + id + " not found.");
                return;
            }
            Milestone existing = db.getMilestoneForTicket(id);
            if (existing != null) {
                CommandErrors.add(input, outputs, "Tickets " + id +
                        " already assigned to milestone "
                        + existing.getName() + ".");
                return;
            }
            tickets.add(ticket);
        }
        List<String> developers = (List<String>) params.getOrDefault("assignedDevs", List.of());
        for (String developer : developers) {
            User candidate = db.getUser(developer);
            if (candidate == null || candidate.asDeveloper() == null) {
                CommandErrors.add(input, outputs, "Developer " + developer + " does not exist.");
                return;
            }
        }
        List<String> blockedNames = (List<String>) params.getOrDefault("blockingFor", List.of());
        List<Milestone> blocked = new ArrayList<>();
        for (String target : blockedNames) {
            Milestone existing = db.getMilestone(target);
            if (existing == null) {
                CommandErrors.add(input, outputs, "Blocked milestone " + target +
                        " does not exist.");
                return;
            }
            blocked.add(existing);
        }
        if (new HashSet<>(developers).size() != developers.size()
                || new HashSet<>(blockedNames).size() != blockedNames.size()) {
            CommandErrors.add(input, outputs,
                    "Milestone developers and dependencies must be unique.");
            return;
        }
        LocalDate start = LocalDate.parse((String) params.getOrDefault("startDate",
                input.getTimestamp()));
        LocalDate due = LocalDate.parse((String) params.get("dueDate"));
        Milestone milestone = new Milestone(name, start, due);
        milestone.setCreator(input.getUsername());
        tickets.forEach(milestone::addTicket);
        developers.forEach(milestone::addDeveloper);
        milestone.setBlockingFor(blockedNames);
        db.addMilestone(milestone);
        blocked.forEach(target -> target.addDependency(milestone));
        for (Ticket ticket : tickets) {
            TicketHistoryEntry entry = new TicketHistoryEntry(name, null, null,
                    input.getUsername(), input.getTimestamp(), HistoryActionType.ADDED_TO_MILESTONE,
                    null, null);
            ticket.addHistoryEntry(entry);
        }
        db.getNotifications().notifyMilestone(milestone, "MILESTONE_CREATED", input.getUsername());
    }
}
