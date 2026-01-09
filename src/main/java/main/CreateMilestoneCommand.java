package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CreateMilestoneCommand implements Command {

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

        if (user.getRole() != UserRole.MANAGER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".");
            outputs.add(error);
            return;
        }

        Map<String, Object> params = input.params;
        List<Integer> ticketIds = (List<Integer>) params.get("tickets");
        List<Ticket> ticketsToAdd = new ArrayList<>();

        if (ticketIds != null) {
            for (Integer id : ticketIds) {
                Ticket foundTicket = null;
                for (Ticket t : db.getTickets()) {
                    if (t.getId().equals(id)) {
                        foundTicket = t;
                        break;
                    }
                }

                if (foundTicket == null) {
                    ObjectNode error = mapper.createObjectNode();
                    error.put("command", input.command);
                    error.put("username", input.username);
                    error.put("timestamp", input.timestamp);
                    error.put("error", "Ticket ID " + id + " not found.");
                    outputs.add(error);
                    return;
                }

                Milestone existingMilestone = db.getMilestoneForTicket(id);
                if (existingMilestone != null) {
                    ObjectNode error = mapper.createObjectNode();
                    error.put("command", input.command);
                    error.put("username", input.username);
                    error.put("timestamp", input.timestamp);
                    error.put("error", "Tickets " + id + " already assigned to milestone " + existingMilestone.getName() + ".");                    outputs.add(error);
                    return;
                }

                ticketsToAdd.add(foundTicket);
            }
        }

        String name = (String) params.get("name");
        String startDateStr = (String) params.get("startDate");
        LocalDate startDate;

        if (startDateStr != null) {
            startDate = LocalDate.parse(startDateStr);
        } else {
            startDate = LocalDate.parse(input.timestamp);
        }

        LocalDate dueDate = LocalDate.parse((String) params.get("dueDate"));

        Milestone milestone = new Milestone(name, startDate, dueDate);
        milestone.setCreator(input.username);

        for (Ticket t : ticketsToAdd) {
            milestone.addTicket(t);

            TicketHistoryEntry entry = new TicketHistoryEntry(
                    HistoryActionType.ADDED_TO_MILESTONE,
                    input.timestamp,
                    input.username,
                    "Added to milestone: " + name
            );
            entry.setMilestone(name);
            t.addHistoryEntry(entry);
        }

        List<String> assignedDevs = (List<String>) params.get("assignedDevs");
        if (assignedDevs != null) {
            for (String devUsername : assignedDevs) {
                milestone.addDeveloper(devUsername);
            }
        }

        List<String> blockingFor = (List<String>) params.get("blockingFor");
        if (blockingFor != null) {
            milestone.setBlockingFor(blockingFor);

            for (String blockedMilestoneName : blockingFor) {
                for (Milestone existing : db.getMilestones()) {
                    if (existing.getName().equals(blockedMilestoneName)) {
                        existing.addDependency(milestone);
                    }
                }
            }
        }

        db.addMilestone(milestone);

        NotificationService.getInstance().notifyMilestone(milestone, "MILESTONE_CREATED", input.username);
    }
}