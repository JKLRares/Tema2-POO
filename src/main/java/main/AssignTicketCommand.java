package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssignTicketCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.params.get("ticketID");
        String username = input.username;

        User user = db.getUser(username);
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + username + " does not exist.");
            outputs.add(error);
            return;
        }

        if (user.getRole() != UserRole.DEVELOPER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + username + " is not a developer.");
            outputs.add(error);
            return;
        }

        DeveloperUser dev = (DeveloperUser) user;
        Ticket ticket = null;
        for (Ticket t : db.getTickets()) {
            if (t.getId().equals(ticketId)) {
                ticket = t;
                break;
            }
        }

        if (ticket == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Ticket " + ticketId + " does not exist.");
            outputs.add(error);
            return;
        }

        if (ticket.getStatus() != Status.OPEN) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Only OPEN tickets can be assigned.");
            outputs.add(error);
            return;
        }

        Milestone milestone = db.getMilestoneForTicket(ticketId);
        if (milestone == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The ticket " + ticketId + " is not assigned to any milestone.");
            outputs.add(error);
            return;
        }

        if (!milestone.getAssignedDevs().contains(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Developer " + username + " is not assigned to milestone " + milestone.getName() + ".");
            outputs.add(error);
            return;
        }

        if (milestone.isBlocked()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Cannot assign ticket " + ticketId + " from blocked milestone " + milestone.getName() + ".");
            outputs.add(error);
            return;
        }

        Expertise ticketCategory = ticket.getCategory();
        if (!hasExpertise(dev.getExpertise(), ticketCategory)) {
            List<String> required = getRequiredExpertise(ticketCategory);
            Collections.sort(required);

            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Developer " + username + " cannot assign ticket " + ticketId +
                    " due to expertise area. Required: " + String.join(", ", required) +
                    "; Current: " + dev.getExpertise() + ".");
            outputs.add(error);
            return;
        }

        if (!hasSeniority(dev.getSeniority(), ticket)) {
            List<String> required = getRequiredSeniority(ticket);
            Collections.sort(required);

            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Developer " + username + " cannot assign ticket " + ticketId +
                    " due to seniority level. Required: " + String.join(", ", required) +
                    "; Current: " + dev.getSeniority() + ".");
            outputs.add(error);
            return;
        }

        ticket.setAssignedTo(username);
        ticket.setStatus(Status.IN_PROGRESS);
        ticket.setAssignedAt(input.timestamp);

        TicketHistoryEntry assignEntry = new TicketHistoryEntry();
        assignEntry.setAction(HistoryActionType.ASSIGNED);
        assignEntry.setTimestamp(input.timestamp);
        assignEntry.setUser(username);
        ticket.addHistoryEntry(assignEntry);

        TicketHistoryEntry statusEntry = new TicketHistoryEntry();
        statusEntry.setAction(HistoryActionType.STATUS_CHANGED);
        statusEntry.setTimestamp(input.timestamp);
        statusEntry.setUser(username);
        statusEntry.setFromStatus(Status.OPEN);
        statusEntry.setToStatus(Status.IN_PROGRESS);
        ticket.addHistoryEntry(statusEntry);
    }

    private boolean hasExpertise(Expertise devExp, Expertise ticketExp) {
        if (devExp == Expertise.FULLSTACK) return true;
        if (devExp == ticketExp) return true;

        if (devExp == Expertise.FRONTEND && ticketExp == Expertise.DESIGN) return true;
        if (devExp == Expertise.BACKEND && ticketExp == Expertise.DB) return true;
        if (devExp == Expertise.DESIGN && ticketExp == Expertise.FRONTEND) return true;

        return false;
    }

    private List<String> getRequiredExpertise(Expertise ticketExp) {
        List<String> req = new ArrayList<>();
        req.add("FULLSTACK");

        switch (ticketExp) {
            case FRONTEND:
                req.add("FRONTEND");
                req.add("DESIGN");
                break;
            case BACKEND:
                req.add("BACKEND");
                break;
            case DEVOPS:
                req.add("DEVOPS");
                break;
            case DESIGN:
                req.add("DESIGN");
                req.add("FRONTEND");
                break;
            case DB:
                req.add("DB");
                req.add("BACKEND");
                break;
            default:
                if (!req.contains(ticketExp.name())) req.add(ticketExp.name());
                break;
        }
        return req;
    }

    private boolean hasSeniority(Seniority devSen, Ticket t) {
        if (devSen == Seniority.SENIOR) return true;

        boolean isMidOk = (t.getPriority() != Priority.CRITICAL);
        if (devSen == Seniority.MID && isMidOk) return true;

        boolean isJuniorOk = (t.getPriority() == Priority.LOW || t.getPriority() == Priority.MEDIUM)
                && (t.getType() == TicketType.BUG || t.getType() == TicketType.UI_FEEDBACK);
        if (devSen == Seniority.JUNIOR && isJuniorOk) return true;

        return false;
    }

    private List<String> getRequiredSeniority(Ticket t) {
        List<String> req = new ArrayList<>();
        req.add("SENIOR");

        boolean isMidOk = (t.getPriority() != Priority.CRITICAL);
        if (isMidOk) req.add("MID");

        boolean isJuniorOk = (t.getPriority() == Priority.LOW || t.getPriority() == Priority.MEDIUM)
                && (t.getType() == TicketType.BUG || t.getType() == TicketType.UI_FEEDBACK);
        if (isJuniorOk) req.add("JUNIOR");

        return req;
    }
}