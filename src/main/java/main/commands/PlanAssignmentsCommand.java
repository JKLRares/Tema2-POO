package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import main.Database;
import main.Milestone;
import main.planning.AssignmentPlanner;
import main.users.User;

/** Produces a proposed assignment plan without changing tickets, histories or notifications. */
public final class PlanAssignmentsCommand implements Command {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AssignmentPlanner planner = new AssignmentPlanner();
    @Override
    public void execute(final InputCommand input, final Database db, final List<ObjectNode>
            outputs) {
        User user = db.getUser(input.getUsername());
        if (user == null || user.asManager() == null) {
            CommandErrors.add(input, outputs, "Only managers can plan assignments.");
            return;
        }
        String name = (String) input.getParams().get("milestone");
        Milestone milestone = db.getMilestone(name);
        if (milestone == null || !input.getUsername().equals(milestone.getCreator())) {
            CommandErrors.add(input, outputs, "Milestone " + name +
                    " is not owned by this manager.");
            return;
        }
        int capacity = (Integer) input.getParams().getOrDefault("capacityPerDeveloper", 1);
        AssignmentPlanner.Plan plan = planner.plan(db, milestone,
                LocalDate.parse(input.getTimestamp()), capacity);
        ObjectNode result = mapper.createObjectNode();
        result.put("command", input.getCommand());
        result.put("username", input.getUsername());
        result.put("timestamp", input.getTimestamp());
        result.set("plan", mapper.valueToTree(plan));
        outputs.add(result);
    }
}
