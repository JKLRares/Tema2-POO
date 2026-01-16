package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;

public class StartTestingPhaseCommand implements Command {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);

        if (user == null || user.getRole() != UserRole.MANAGER) {
            ObjectNode error = mapper.createObjectNode();
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "The user " + input.username + " does not have permission to execute this command: required role MANAGER.");
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
            error.put("command", input.command);
            error.put("username", input.username);
            error.put("timestamp", input.timestamp);
            error.put("error", "Cannot start testing phase with active milestones.");
            outputs.add(error);
            return;
        }

        db.setTestingPhase(true);
        db.setTestingPhaseStartDate(LocalDate.parse(input.timestamp));
    }
}