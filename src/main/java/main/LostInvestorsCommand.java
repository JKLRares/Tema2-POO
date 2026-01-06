package main;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

public class LostInvestorsCommand implements Command {

    @Override
    public void execute(InputCommand input, Database db, List<ObjectNode> outputs) {
        db.setTestingPhase(false);
    }
}