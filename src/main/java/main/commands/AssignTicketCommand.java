package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import main.Database;
import main.Milestone;
import main.enums.Expertise;
import main.enums.Status;
import main.enums.Priority;
import main.enums.Seniority;
import main.enums.TicketType;
import main.enums.HistoryActionType;
import main.tickets.Ticket;
import main.tickets.TicketHistoryEntry;
import main.users.DeveloperUser;
import main.users.User;

public final class AssignTicketCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        Integer ticketId = (Integer) input.getParams().get("ticketID");
        String username = input.getUsername();

        User user = db.getUser(username);
        if (user == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + username + " does not exist.");
            outputs.add(error);
            return;
        }

        DeveloperUser dev = user.asDeveloper();
        if (dev == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + username + " is not a developer.");
            outputs.add(error);
            return;
        }

        Ticket ticket = null;
        for (Ticket t : db.getTickets()) {
            if (t.getId().equals(ticketId)) {
                ticket = t;
                break;
            }
        }

        if (ticket == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Ticket " + ticketId + " does not exist.");
            outputs.add(error);
            return;
        }

        if (ticket.getStatus() != Status.OPEN) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Only OPEN tickets can be assigned.");
            outputs.add(error);
            return;
        }

        Milestone milestone = db.getMilestoneForTicket(ticketId);
        if (milestone == null) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The ticket " + ticketId + " is not assigned to any milestone.");
            outputs.add(error);
            return;
        }

        if (!milestone.getAssignedDevs().contains(username)) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Developer " + username + " is not assigned to milestone "
                    + milestone.getName() + ".");
            outputs.add(error);
            return;
        }

        // Cannot assign tickets from a milestone that is blocked by unresolved dependencies
        if (milestone.isBlocked()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Cannot assign ticket " + ticketId + " from blocked milestone "
                    + milestone.getName() + ".");
            outputs.add(error);
            return;
        }

        Expertise ticketCategory = ticket.getCategory();
        // Verify compatibility between the developer's expertise and the ticket category
        if (!hasExpertise(dev.getExpertise(), ticketCategory)) {
            List<String> required = getRequiredExpertise(ticketCategory);
            Collections.sort(required);

            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Developer " + username + " cannot assign ticket " + ticketId
                    + " due to expertise area. Required: " + String.join(", ", required)
                    + "; Current: " + dev.getExpertise() + ".");
            outputs.add(error);
            return;
        }

        /* Juniors can only take Low/Medium tickets of type Bug/UI;
        Mid and Senior developers have extended access
         */
        if (!hasSeniority(dev.getSeniority(), ticket)) {
            List<String> required = getRequiredSeniority(ticket);
            Collections.sort(required);

            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", username);
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Developer " + username + " cannot assign ticket " + ticketId
                    + " due to seniority level. Required: " + String.join(", ", required)
                    + "; Current: " + dev.getSeniority() + ".");
            outputs.add(error);
            return;
        }

        ticket.setAssignedTo(username);
        ticket.setStatus(Status.IN_PROGRESS);
        ticket.setAssignedAt(input.getTimestamp());

        TicketHistoryEntry assignEntry = new TicketHistoryEntry();
        assignEntry.setAction(HistoryActionType.ASSIGNED);
        assignEntry.setTimestamp(input.getTimestamp());
        assignEntry.setUser(username);
        ticket.addHistoryEntry(assignEntry);

        TicketHistoryEntry statusEntry = new TicketHistoryEntry();
        statusEntry.setAction(HistoryActionType.STATUS_CHANGED);
        statusEntry.setTimestamp(input.getTimestamp());
        statusEntry.setUser(username);
        statusEntry.setFromStatus(Status.OPEN);
        statusEntry.setToStatus(Status.IN_PROGRESS);
        ticket.addHistoryEntry(statusEntry);
    }

    private boolean hasExpertise(final Expertise devExp, final Expertise ticketExp) {
        if (devExp == Expertise.FULLSTACK) {
            return true;
        }
        if (devExp == ticketExp) {
            return true;
        }

        if (devExp == Expertise.FRONTEND && ticketExp == Expertise.DESIGN) {
            return true;
        }
        if (devExp == Expertise.BACKEND && ticketExp == Expertise.DB) {
            return true;
        }
        if (devExp == Expertise.DESIGN && ticketExp == Expertise.FRONTEND) {
            return true;
        }

        return false;
    }

    private List<String> getRequiredExpertise(final Expertise ticketExp) {
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
                if (!req.contains(ticketExp.name())) {
                    req.add(ticketExp.name());
                }
                break;
        }
        return req;
    }

    private boolean hasSeniority(final Seniority devSen, final Ticket t) {
        if (devSen == Seniority.SENIOR) {
            return true;
        }

        boolean isMidOk = (t.getPriority() != Priority.CRITICAL);
        if (devSen == Seniority.MID && isMidOk) {
            return true;
        }

        boolean isJuniorOk = (t.getPriority() == Priority.LOW || t.getPriority() == Priority.MEDIUM)
                && (t.getType() == TicketType.BUG || t.getType() == TicketType.UI_FEEDBACK);
        if (devSen == Seniority.JUNIOR && isJuniorOk) {
            return true;
        }

        return false;
    }

    private List<String> getRequiredSeniority(final Ticket t) {
        List<String> req = new ArrayList<>();
        req.add("SENIOR");

        boolean isMidOk = (t.getPriority() != Priority.CRITICAL);
        if (isMidOk) {
            req.add("MID");
        }

        boolean isJuniorOk = (t.getPriority() == Priority.LOW || t.getPriority() == Priority.MEDIUM)
                && (t.getType() == TicketType.BUG || t.getType() == TicketType.UI_FEEDBACK);
        if (isJuniorOk) {
            req.add("JUNIOR");
        }

        return req;
    }
}
