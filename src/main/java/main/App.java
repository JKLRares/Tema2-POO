package main;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Batch adapter and command-line entry point for the ticket tracker. */
public final class App {
    private static final List<String> PATH_FLAGS = List.of("--users", "--input", "--output");
    private static final Path COURSEWORK_USERS = Path.of("input/database/users.json");
    private static final String USAGE = "Usage: java -jar bug-tracker.jar --users USERS.json "
            + "--input COMMANDS.json --output RESULTS.json [--overwrite]";

    private App() {
    }

    /** Compatibility entry point used by the original university checker. */
    public static void run(final String inputPath, final String outputPath) throws IOException {
        run(COURSEWORK_USERS, Path.of(inputPath), Path.of(outputPath), true);
    }

    /** Parses the whole input before publishing output; failures preserve any existing file. */
    public static void run(final Path users, final Path input, final Path output,
                           final boolean overwrite) throws IOException {
        Path destination = output.toAbsolutePath().normalize();
        if (Files.exists(destination)) {
            if (Files.isSameFile(destination, users) || Files.isSameFile(destination, input)) {
                throw new IOException("Output must differ from the users and command files.");
            }
            if (!overwrite) {
                throw new IOException("Output already exists; use --overwrite to replace it.");
            }
        }
        ObjectMapper mapper = JsonSupport.mapper();
        JsonNode commands = mapper.readTree(input.toFile());
        if (commands == null || !commands.isArray()) {
            throw new IOException("Commands file must contain an array.");
        }
        TrackerSession session = new TrackerSession(mapper.readTree(users.toFile()));
        List<ObjectNode> outputs = new ArrayList<>();
        for (JsonNode command : commands) {
            outputs.addAll(session.execute(command));
        }
        writeOutput(mapper, destination, outputs, overwrite);
    }

    private static void writeOutput(final ObjectMapper mapper, final Path destination,
                                    final List<ObjectNode> outputs, final boolean overwrite)
            throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".bug-tracker-", ".tmp");

        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), outputs);
            if (overwrite) {
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    throw new IOException(
                            "Filesystem does not support atomic output replacement.", exception);
                }
            } else {
                // A non-replacing move protects files created after the initial existence check.
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Runs the CLI and exits with a nonzero status only on adapter failure. */
    public static void main(final String[] args) {
        int status = runCli(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /** Returns an exit code, allowing CLI behavior to be tested without terminating the JVM. */
    public static int runCli(final String[] args, final PrintStream output,
                             final PrintStream error) {
        if (args.length == 1 && "--help".equals(args[0])) {
            output.println(USAGE);
            return 0;
        }
        Map<String, String> paths = new HashMap<>();
        boolean overwrite = false;
        for (int index = 0; index < args.length; index++) {
            String flag = args[index];
            if ("--overwrite".equals(flag) && !overwrite) {
                overwrite = true;
            } else if (PATH_FLAGS.contains(flag)
                    && index + 1 < args.length && !paths.containsKey(flag)
                    && !args[index + 1].startsWith("--")) {
                paths.put(flag, args[++index]);
            } else {
                error.println(USAGE);
                return 2;
            }
        }
        if (paths.size() != PATH_FLAGS.size()) {
            error.println(USAGE);
            return 2;
        }

        try {
            run(Path.of(paths.get("--users")), Path.of(paths.get("--input")),
                    Path.of(paths.get("--output")), overwrite);
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            error.println("Cannot process batch: " + exception.getMessage());
            return 1;
        }
    }
}
