package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.tickets.Ticket;
import main.enums.Status;
import main.enums.UserRole;
import main.users.User;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ViewTicketsCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db, final List<ObjectNode>
            outputs) {
        User user = db.getUser(input.getUsername());
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("status", "error");
            error.put("description", "User not found: " + input.getUsername());
            outputs.add(error);
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.getTimestamp());
        db.refreshMilestones(currentDate);

        List<Ticket> allTickets = db.getTickets();
        List<Ticket> filteredTickets;

        if (user.getRole() == UserRole.MANAGER) {
            filteredTickets = allTickets;
        } else if (user.getRole() == UserRole.REPORTER) {
            filteredTickets = allTickets.stream()
                    .filter(t -> t.getReportedBy().equals(user.getUsername()))
                    .collect(Collectors.toList());
        } else if (user.getRole() == UserRole.DEVELOPER) {
            filteredTickets = db.getMilestones().stream()
                    .filter(m -> m.getAssignedDevs().contains(user.getUsername()))
                    .flatMap(m -> m.getTickets().stream())
                    .filter(t -> t.getStatus() == Status.OPEN)
                    .collect(Collectors.toList());
        } else {
            filteredTickets = List.of();
        }

        List<Ticket> sortedTickets = filteredTickets.stream()
                .sorted(Comparator.comparing(Ticket::getCreatedAt)
                        .thenComparing(Ticket::getId))
                .collect(Collectors.toList());

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        resultNode.set("tickets", mapper.valueToTree(sortedTickets));

        outputs.add(resultNode);
    }
}
