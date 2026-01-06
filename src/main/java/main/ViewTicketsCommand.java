package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ViewTicketsCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("status", "error");
            error.put("description", "User not found: " + input.username);
            outputs.add(error);
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.timestamp);
        for (Milestone m : db.getMilestones()) {
            m.updateStatusAndPriorities(currentDate);
        }

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
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        resultNode.set("tickets", mapper.valueToTree(sortedTickets));

        outputs.add(resultNode);
    }
}