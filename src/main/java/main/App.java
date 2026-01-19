package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import main.commands.*;
import main.users.User;

/**
 * main.App represents the main application logic that processes input commands,
 * generates outputs, and writes them to a file
 */
final public class App {
    private App() {
    }

    private static final String INPUT_USERS_FIELD = "input/database/users.json";

    private static final ObjectWriter WRITER =
            new ObjectMapper().writer().withDefaultPrettyPrinter();

    private static final Map<String, Command> CommandRegistry = new HashMap<>();

    static {
        CommandRegistry.put("reportTicket", new ReportTicketCommand());
        CommandRegistry.put("viewTickets", new ViewTicketsCommand());
        CommandRegistry.put("lostInvestors", new LostInvestorsCommand());
        CommandRegistry.put("createMilestone", new CreateMilestoneCommand());
        CommandRegistry.put("viewMilestones", new ViewMilestonesCommand());
        CommandRegistry.put("assignTicket", new AssignTicketCommand());
        CommandRegistry.put("viewAssignedTickets", new ViewAssignedTicketsCommand());
        CommandRegistry.put("undoAssignTicket", new UndoAssignTicketCommand());
        CommandRegistry.put("addComment", new AddCommentCommand());
        CommandRegistry.put("undoAddComment", new UndoAddCommentCommand());
        CommandRegistry.put("changeStatus", new ChangeStatusCommand());
        CommandRegistry.put("undoChangeStatus", new UndoChangeStatusCommand());
        CommandRegistry.put("viewTicketHistory", new ViewTicketHistoryCommand());
        CommandRegistry.put("viewNotifications", new ViewNotificationsCommand());
        CommandRegistry.put("generateCustomerImpactReport",
                new GenerateCustomerImpactReportCommand());
        CommandRegistry.put("generateTicketRiskReport", new GenerateTicketRiskReportCommand());
        CommandRegistry.put("generateResolutionEfficiencyReport",
                new GenerateResolutionEfficiencyReportCommand());
        CommandRegistry.put("appStabilityReport", new AppStabilityReportCommand());
        CommandRegistry.put("generatePerformanceReport", new GeneratePerformanceReportCommand());
        CommandRegistry.put("search", new SearchCommand());
        CommandRegistry.put("startTestingPhase", new StartTestingPhaseCommand());
    }

    /**
     * Runs the application: reads commands from an input file,
     * processes them, generates results, and writes them to an output file
     *
     * @param inputPath path to the input file containing commands
     * @param outputPath path to the file where results should be written
     */
    public static void run(final String inputPath, final String outputPath) {
        List<ObjectNode> outputs = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        Database.reset();
        Database db = Database.getInstance();

        try {
            User[] users = mapper.readValue(new File(INPUT_USERS_FIELD), User[].class);
            for (User user : users) {
                db.addUser(user);
            }

            InputCommand[] commands = mapper.readValue(new File(inputPath), InputCommand[].class);

            for (InputCommand inputCommand : commands) {
                Command command = CommandRegistry.get(inputCommand.getCommand());

                if (command != null) {
                    command.execute(inputCommand, db, outputs);
                } else {
                    System.out.println("Unknown command: " + inputCommand.getCommand());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            WRITER.withDefaultPrettyPrinter().writeValue(outputFile, outputs);
        } catch (IOException e) {
            System.out.println("error writing to output file: " + e.getMessage());
        }
    }
}
