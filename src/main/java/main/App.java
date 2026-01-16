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
                    case "addComment":
                        command = new AddCommentCommand();
                        break;
                    case "undoAddComment":
                        command = new UndoAddCommentCommand();
                        break;
                    case "changeStatus":
                        command = new ChangeStatusCommand();
                        break;
                    case "undoChangeStatus":
                        command = new UndoChangeStatusCommand();
                        break;
                    case "viewTicketHistory":
                        command = new ViewTicketHistoryCommand();
                        break;
                    case "viewNotifications":
                        command = new ViewNotificationsCommand();
                        break;
                    case "generateCustomerImpactReport":
                        command = new GenerateCustomerImpactReportCommand();
                        break;
                    case "generateTicketRiskReport":
                        command = new GenerateTicketRiskReportCommand();
                        break;
                    case "generateResolutionEfficiencyReport":
                        command = new GenerateResolutionEfficiencyReportCommand();
                        break;
                    case "appStabilityReport":
                        command = new AppStabilityReportCommand();
                        break;
                    case "generatePerformanceReport":
                        command = new GeneratePerformanceReportCommand();
                        break;
                    case "search":
                        command = new SearchCommand();
                        break;
                    case "startTestingPhase":
                        command = new StartTestingPhaseCommand();
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
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            WRITER.withDefaultPrettyPrinter().writeValue(outputFile, outputs);
        } catch (IOException e) {
            System.out.println("error writing to output file: " + e.getMessage());
        }
    }
}
