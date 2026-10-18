package main.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import main.Database;

import java.util.List;

public final class LostInvestorsCommand implements Command {

    @Override
    public void execute(final InputCommand input, final Database db, final List<ObjectNode>
            outputs) {
        if (db.getUser(input.getUsername()) == null
                || db.getUser(input.getUsername()).asManager() == null) {
            CommandErrors.add(input, outputs, "Only managers can end a testing phase.");
            return;
        }
        db.setTestingPhase(false);
    }
}
