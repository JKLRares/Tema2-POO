package main.commands;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Common envelope for validation errors; legacy command responses retain their wire format. */
public final class CommandErrors {
    private CommandErrors() {
    }

    /** Creates an error snapshot without changing the input or session. */
    public static ObjectNode error(final InputCommand input, final String message) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("command", input == null ? null : input.getCommand());
        node.put("username", input == null ? null : input.getUsername());
        node.put("timestamp", input == null ? null : input.getTimestamp());
        node.put("error", message);
        return node;
    }

    /** Appends a validation response using the standard command envelope. */
    public static void add(final InputCommand input, final List<ObjectNode> outputs,
                           final String message) {
        outputs.add(error(input, message));
    }
}
