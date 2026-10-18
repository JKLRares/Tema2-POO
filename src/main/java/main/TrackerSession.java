package main;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import main.commands.AddCommentCommand;
import main.commands.AppStabilityReportCommand;
import main.commands.AssignTicketCommand;
import main.commands.ChangeStatusCommand;
import main.commands.Command;
import main.commands.CommandErrors;
import main.commands.CommandValidator;
import main.commands.CreateMilestoneCommand;
import main.commands.GenerateCustomerImpactReportCommand;
import main.commands.GeneratePerformanceReportCommand;
import main.commands.GenerateResolutionEfficiencyReportCommand;
import main.commands.GenerateTicketRiskReportCommand;
import main.commands.InputCommand;
import main.commands.InvalidCommandException;
import main.commands.LostInvestorsCommand;
import main.commands.PlanAssignmentsCommand;
import main.commands.ReportTicketCommand;
import main.commands.SearchCommand;
import main.commands.StartTestingPhaseCommand;
import main.commands.UndoAddCommentCommand;
import main.commands.UndoAssignTicketCommand;
import main.commands.UndoChangeStatusCommand;
import main.commands.ViewAssignedTicketsCommand;
import main.commands.ViewMilestonesCommand;
import main.commands.ViewNotificationsCommand;
import main.commands.ViewTicketHistoryCommand;
import main.commands.ViewTicketsCommand;
import main.users.DeveloperUser;
import main.users.ManagerUser;
import main.users.User;

/** One isolated, single-threaded command session. Callers exchange JSON snapshots, not entities. */
public final class TrackerSession {
    private static final Map<String, Command> COMMANDS = Map.ofEntries(
            Map.entry("reportTicket", new ReportTicketCommand()),
            Map.entry("viewTickets", new ViewTicketsCommand()),
            Map.entry("lostInvestors", new LostInvestorsCommand()),
            Map.entry("createMilestone", new CreateMilestoneCommand()),
            Map.entry("viewMilestones", new ViewMilestonesCommand()),
            Map.entry("assignTicket", new AssignTicketCommand()),
            Map.entry("viewAssignedTickets", new ViewAssignedTicketsCommand()),
            Map.entry("undoAssignTicket", new UndoAssignTicketCommand()),
            Map.entry("addComment", new AddCommentCommand()),
            Map.entry("undoAddComment", new UndoAddCommentCommand()),
            Map.entry("changeStatus", new ChangeStatusCommand()),
            Map.entry("undoChangeStatus", new UndoChangeStatusCommand()),
            Map.entry("viewTicketHistory", new ViewTicketHistoryCommand()),
            Map.entry("viewNotifications", new ViewNotificationsCommand()),
            Map.entry("generateCustomerImpactReport", new GenerateCustomerImpactReportCommand()),
            Map.entry("generateTicketRiskReport", new GenerateTicketRiskReportCommand()),
            Map.entry("generateResolutionEfficiencyReport",
                    new GenerateResolutionEfficiencyReportCommand()),
            Map.entry("appStabilityReport", new AppStabilityReportCommand()),
            Map.entry("generatePerformanceReport", new GeneratePerformanceReportCommand()),
            Map.entry("search", new SearchCommand()),
            Map.entry("startTestingPhase", new StartTestingPhaseCommand()),
            Map.entry("planAssignments", new PlanAssignmentsCommand())
    );
    private final ObjectMapper mapper = JsonSupport.mapper();
    private final Database db = new Database();

    /** Creates fresh users; changes to the input tree cannot affect this session. */
    public TrackerSession(final JsonNode users) throws IOException {
        if (users == null || !users.isArray()) {
            throw new IOException("Users file must contain an array.");
        }
        try {
            for (JsonNode node : users) {
                validateUser(node);
                db.addUser(mapper.treeToValue(node, User.class));
            }
            for (User user : db.getUsers()) {
                ManagerUser manager = user.asManager();
                if (manager != null && manager.getSubordinates() != null) {
                    for (String subordinate : manager.getSubordinates()) {
                        User candidate = db.getUser(subordinate);
                        if (candidate == null || candidate.asDeveloper() == null) {
                            throw new InvalidCommandException("Unknown subordinate: " + subordinate);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid users: " + exception.getMessage(), exception);
        }
    }

    /** Executes a single command, returning independent response snapshots. */
    public List<ObjectNode> execute(final JsonNode raw) {
        InputCommand input = new InputCommand();
        List<ObjectNode> outputs = new ArrayList<>();
        if (raw != null && raw.isObject()) {
            input.setCommand(raw.path("command").isTextual() ? raw.get("command").asText() : null);
            input.setUsername(raw.path("username").isTextual() ? raw.get("username").asText() :
                    null);
            input.setTimestamp(raw.path("timestamp").isTextual() ? raw.get("timestamp").asText() :
                    null);
        }
        try {
            if (raw == null || !raw.isObject()) {
                throw new InvalidCommandException("Command must be an object.");
            }
            input.setParams(parameters(raw));
            CommandValidator.validateEnvelope(input);
            Command handler = COMMANDS.get(input.getCommand());
            if (handler == null) {
                throw new InvalidCommandException("Unknown command: " + input.getCommand());
            }
            // Preserve legacy user-error precedence, including obsolete fields in rejected
            // requests.
            if (db.getUser(input.getUsername()) != null
                    || !"reportTicket".equals(input.getCommand())) {
                CommandValidator.validateParameters(input);
            }
            handler.execute(input, db, outputs);
        } catch (InvalidCommandException exception) {
            outputs.add(CommandErrors.error(input, exception.getMessage()));
        }
        return List.copyOf(outputs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters(final JsonNode raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        JsonNode nested = raw.get("params");
        if (nested != null) {
            if (!nested.isObject()) {
                throw new InvalidCommandException("params must be an object.");
            }
            result.putAll(mapper.convertValue(nested, Map.class));
        }
        Iterator<Map.Entry<String, JsonNode>> fields = raw.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (!List.of("command", "username", "timestamp", "params").contains(key)) {
                if (result.containsKey(key)) {
                    throw new InvalidCommandException("Parameter provided twice: " + key);
                }
                result.put(key, mapper.convertValue(field.getValue(), Object.class));
            }
        }
        return result;
    }

    private void validateUser(final JsonNode user) {
        if (user == null || !user.isObject()) {
            throw new InvalidCommandException("User must be an object.");
        }
        for (String field : List.of("username", "role")) {
            if (!user.path(field).isTextual() || user.get(field).asText().isBlank()) {
                throw new InvalidCommandException("User " + field + " must be a nonblank string.");
            }
        }
        if (user.has("hireDate")) {
            CommandValidator.date(user.get("hireDate").isTextual()
                    ? user.get("hireDate").asText() : null, "hireDate");
        }
        if ("DEVELOPER".equals(user.get("role").asText())) {
            DeveloperUser developer = mapper.convertValue(user, DeveloperUser.class);
            if (developer.getExpertise() == null || developer.getSeniority() == null) {
                throw new InvalidCommandException(
                        "Developers require expertiseArea and seniority.");
            }
        }
        if ("MANAGER".equals(user.get("role").asText()) && user.has("subordinates")) {
            JsonNode subordinates = user.get("subordinates");
            if (!subordinates.isArray()) {
                throw new InvalidCommandException("subordinates must be an array.");
            }
            for (JsonNode subordinate : subordinates) {
                if (!subordinate.isTextual() || subordinate.asText().isBlank()) {
                    throw new InvalidCommandException("subordinates must contain usernames.");
                }
            }
        }
    }
}
