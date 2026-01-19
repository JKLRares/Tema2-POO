package main.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;

import java.util.List;

public interface Command {
    void execute(InputCommand input, Database db, List<ObjectNode> outputs);
}
