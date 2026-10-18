package main.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;
import main.users.User;

import java.time.LocalDate;
import java.util.List;

public final class ViewNotificationsCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(final InputCommand input, final Database db, final List<ObjectNode>
            outputs) {
        User user = db.getUser(input.getUsername());

        if (user == null) {
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.getTimestamp());
        db.refreshMilestones(currentDate);

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.getCommand());
        resultNode.put("username", input.getUsername());
        resultNode.put("timestamp", input.getTimestamp());

        ArrayNode notificationsArray = mapper.createArrayNode();
        List<String> userNotifications = user.drainNotifications();

        for (String msg : userNotifications) {
            notificationsArray.add(msg);
        }

        resultNode.set("notifications", notificationsArray);
        outputs.add(resultNode);
    }
}
