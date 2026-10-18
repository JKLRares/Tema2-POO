package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.Milestone;
import main.enums.UserRole;
import main.users.User;

import java.time.LocalDate;
import java.util.List;

public final class StartTestingPhaseCommand implements Command {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db,
                        final List<ObjectNode> outputs) {
        User user = db.getUser(input.getUsername());

        if (user == null || user.getRole() != UserRole.MANAGER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "The user " + input.getUsername() + " does not have permission"
                    + "to execute this command: required role MANAGER.");
            outputs.add(error);
            return;
        }

        boolean hasActiveMilestones = false;
        if (db.getMilestones() != null) {
            for (Milestone m : db.getMilestones()) {
                if (!m.areTicketsResolved()) {
                    hasActiveMilestones = true;
                    break;
                }
            }
        }

        if (hasActiveMilestones) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.getCommand());
            error.put("username", input.getUsername());
            error.put("timestamp", input.getTimestamp());
            error.put("error", "Cannot start a new testing phase.");
            outputs.add(error);
            return;
        }

        db.setTestingPhase(true);
        db.setTestingPhaseStartDate(LocalDate.parse(input.getTimestamp()));
    }
}
