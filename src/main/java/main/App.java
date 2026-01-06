package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
/**
 * main.App represents the main application logic that processes input commands,
 * generates outputs, and writes them to a file
 */
public class App {
    private App() {
    }

    private static final String INPUT_USERS_FIELD = "input/database/users.json";

    private static final ObjectWriter WRITER =
            new ObjectMapper().writer().withDefaultPrettyPrinter();

    /**
     * Runs the application: reads commands from an input file,
     * processes them, generates results, and writes them to an output file
     *
     * @param inputPath path to the input file containing commands
     * @param outputPath path to the file where results should be written
     */
    public static void run(final String inputPath, final String outputPath) {
        // feel free to change this if needed
        // however keep 'outputs' variable name to be used for writing
        List<ObjectNode> outputs = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        /*
            TODO 1 :
            Load initial user data and commands. we strongly recommend using jackson library.
            you can use the reading from hw1 as a reference.
            however you can use some of the more advanced features of
            jackson library, available here: https://www.baeldung.com/jackson-annotations
        */
        Database.reset();
        Database db = Database.getInstance();

        try {
            User[] users = mapper.readValue(new File(INPUT_USERS_FIELD), User[].class);
            for (User user : users) {
                db.addUser(user);
            }

            InputCommand[] commands = mapper.readValue(new File(inputPath), InputCommand[].class);

            // TODO 2: process commands.
            for (InputCommand inputCommand : commands) {
                Command command = null;

                switch (inputCommand.command) {
                    case "reportTicket":
                        command = new ReportTicketCommand();
                        break;
                    case "viewTickets":
                        command = new ViewTicketsCommand();
                        break;
                    case "lostInvestors":
                        command = new LostInvestorsCommand();
                        break;
                    case "createMilestone":
                        command = new CreateMilestoneCommand();
                        break;
                    case "viewMilestones":
                        command = new ViewMilestonesCommand();
                        break;
                    case "assignTicket":
                        command = new AssignTicketCommand();
                        break;
                    case "viewAssignedTickets":
                        command = new ViewAssignedTicketsCommand();
                        break;
                    case "undoAssignTicket":
                        command = new UndoAssignTicketCommand();
                        break;
                    default:
                        System.out.println("Unknown command: " + inputCommand.command);
                        break;
                }

                if (command != null) {
                    command.execute(inputCommand, db, outputs);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO 3: create objectnodes for output, add them to outputs list.

        // DO NOT CHANGE THIS SECTION IN ANY WAY
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            WRITER.withDefaultPrettyPrinter().writeValue(outputFile, outputs);
        } catch (IOException e) {
            System.out.println("error writing to output file: " + e.getMessage());
        }
    }
}
