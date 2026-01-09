package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ViewNotificationsCommand implements Command {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        User user = db.getUser(input.username);

        if (user == null) {
            return;
        }

        LocalDate currentDate = LocalDate.parse(input.timestamp);
        for (Milestone m : db.getMilestones()) {
            m.updateStatusAndPriorities(currentDate);
        }

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.put("command", input.command);
        resultNode.put("username", input.username);
        resultNode.put("timestamp", input.timestamp);

        ArrayNode notificationsArray = mapper.createArrayNode();
        List<String> userNotifications = new ArrayList<>(user.getNotifications());

        for (String msg : userNotifications) {
            notificationsArray.add(msg);
        }

        resultNode.set("notifications", notificationsArray);
        outputs.add(resultNode);

        user.getNotifications().clear();
    }
}