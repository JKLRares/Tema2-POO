package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ViewMilestonesCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not exist.");
            outputs.add(error);
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.timestamp);
        for (Milestone m : db.getMilestones()) {
            m.updateStatusAndPriorities(currentDate);
        }

        List<Milestone> filteredMilestones = new ArrayList<>();

        if (user.getRole() == UserRole.MANAGER) {
            filteredMilestones = db.getMilestones().stream()
                    .filter(m -> m.getCreator().equals(user.getUsername()))
                    .collect(Collectors.toList());
        } else if (user.getRole() == UserRole.DEVELOPER) {
            filteredMilestones = db.getMilestones().stream()
                    .filter(m -> m.getAssignedDevs().contains(user.getUsername()))
                    .collect(Collectors.toList());
        }
        filteredMilestones.sort(Comparator.comparing(Milestone::getDueDate)
                .thenComparing(Milestone::getName));

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        ArrayNode milestonesArray = mapper.createArrayNode();

        for (Milestone m : filteredMilestones) {
            ObjectNode mNode = mapper.createObjectNode();

            mNode.put("name", m.getName());

            ArrayNode blockingNode = mapper.createArrayNode();
            if (m.getBlockingFor() != null) {
                m.getBlockingFor().forEach(blockingNode::add);
            }
            mNode.set("blockingFor", blockingNode);

            mNode.put("dueDate", m.getDueDate().toString());
            mNode.put("createdAt", m.getStartDate().toString());

            ArrayNode ticketsNode = mapper.createArrayNode();
            m.getTickets().forEach(t -> ticketsNode.add(t.getId()));
            mNode.set("tickets", ticketsNode);

            ArrayNode devsNode = mapper.createArrayNode();
            if (m.getAssignedDevs() != null) {
                m.getAssignedDevs().forEach(devsNode::add);
            }
            mNode.set("assignedDevs", devsNode);

            mNode.put("createdBy", m.getCreator());

            String status = m.isCompleted() ? "COMPLETED" : "ACTIVE";
            mNode.put("status", status);

            mNode.put("isBlocked", m.isBlocked());

            long rawDiff = ChronoUnit.DAYS.between(currentDate, m.getDueDate());
            long daysUntilDue = rawDiff;

            if (rawDiff >= 0) {
                daysUntilDue = rawDiff + 1;
            } else {
                daysUntilDue = 0;
            }
            mNode.put("daysUntilDue", daysUntilDue);

            long overdueBy = 0;
            if (currentDate.isAfter(m.getDueDate())) {
                overdueBy = ChronoUnit.DAYS.between(m.getDueDate(), currentDate) + 1;
            }
            mNode.put("overdueBy", overdueBy);

            ArrayNode openTicketsNode = mapper.createArrayNode();
            ArrayNode closedTicketsNode = mapper.createArrayNode();
            long resolvedTickets = 0;
            for (Ticket t : m.getTickets()) {
                if (t.getStatus() == Status.CLOSED) {
                    closedTicketsNode.add(t.getId());
                    resolvedTickets++;
                } else {
                    openTicketsNode.add(t.getId());
                }
            }
            mNode.set("openTickets", openTicketsNode);
            mNode.set("closedTickets", closedTicketsNode);

            double completionPercentage = 0.0;
            if (!m.getTickets().isEmpty()) {
                completionPercentage = ((double) resolvedTickets * 100) / m.getTickets().size();
            }
            mNode.put("completionPercentage", completionPercentage);

            ArrayNode repartitionNode = mapper.createArrayNode();
            if (m.getAssignedDevs() != null) {
                for (String devName : m.getAssignedDevs()) {
                    ObjectNode devRepNode = mapper.createObjectNode();
                    devRepNode.put("developer", devName);
                    ArrayNode assignedTicketsNode = mapper.createArrayNode();

                    for (Ticket t : m.getTickets()) {
                        if (devName.equals(t.getAssignedTo())) {
                            assignedTicketsNode.add(t.getId());
                        }
                    }
                    devRepNode.set("assignedTickets", assignedTicketsNode);
                    repartitionNode.add(devRepNode);
                }
            }
            mNode.set("repartition", repartitionNode);

            milestonesArray.add(mNode);
        }

        resultNode.set("milestones", milestonesArray);
        outputs.add(resultNode);
    }
}