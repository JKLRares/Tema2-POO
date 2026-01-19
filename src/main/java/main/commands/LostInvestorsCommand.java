package main.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;

import java.util.List;

public class LostInvestorsCommand implements Command {

    @Override
    public void execute(final InputCommand input, final Database db, final List<ObjectNode> outputs) {
        db.setTestingPhase(false);
    }
}
