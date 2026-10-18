package portfolio;

import main.App;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static portfolio.TestData.JSON;

class BatchIoTest {
    @TempDir Path directory;
    final Path users = Path.of("examples/users.json");

    @ParameterizedTest
    @ValueSource(strings = {"", "[", "null", "{}", "[] {}",
            "[{\"command\":\"viewTickets\",\"command\":\"lostInvestors\"}]",
            "[{\"params\":{\"ticketID\":0,\"ticketID\":1}}]"})
    void malformedFilesNeverReplaceExistingOutput(String content) throws Exception {
        Path input = Files.writeString(directory.resolve("commands.json"), content);
        Path output = Files.writeString(directory.resolve("results.json"), "previous results");
        assertThatThrownBy(() -> App.run(users, input, output, true)).isInstanceOf(IOException.class);
        assertThat(Files.readString(output)).isEqualTo("previous results");
        assertNoTemporaryFiles();
    }

    @Test
    void refusesAccidentalReplacementAndAllowsExplicitReplacement() throws Exception {
        Path input = Files.writeString(directory.resolve("commands.json"), "[]");
        Path output = Files.writeString(directory.resolve("results.json"), "previous results");
        assertThatThrownBy(() -> App.run(users, input, output, false)).isInstanceOf(IOException.class);
        assertThat(Files.readString(output)).isEqualTo("previous results");
        App.run(users, input, output, true);
        assertThat(JSON.readTree(output.toFile())).isEmpty();
        assertNoTemporaryFiles();
    }

    @Test
    void missingUsersDoNotPublishAnEmptySuccessFile() throws Exception {
        Path input = Files.writeString(directory.resolve("commands.json"), "[]");
        Path output = directory.resolve("results.json");
        assertThatThrownBy(() -> App.run(directory.resolve("missing.json"), input, output, false))
                .isInstanceOf(IOException.class);
        assertThat(output).doesNotExist();
    }

    @Test
    void protectsInputsIncludingFilesystemAliases() throws Exception {
        Path input = Files.writeString(directory.resolve("commands.json"), "[]");
        Path ownUsers = Files.copy(users, directory.resolve("users.json"));
        Path symlink = Files.createSymbolicLink(directory.resolve("symlink.json"), input);
        Path hardlink = Files.createLink(directory.resolve("hardlink.json"), ownUsers);
        for (Path output : new Path[]{input, ownUsers, symlink, hardlink}) {
            assertThatThrownBy(() -> App.run(ownUsers, input, output, true))
                    .isInstanceOf(IOException.class).hasMessageContaining("Output must differ");
        }
        assertThat(Files.readString(input)).isEqualTo("[]");
        assertThat(Files.readString(ownUsers)).isEqualTo(Files.readString(users));
    }

    @Test
    void cleansTemporaryOutputIfPublishingFails() throws Exception {
        Path input = Files.writeString(directory.resolve("commands.json"), "[]");
        Path output = Files.createDirectory(directory.resolve("results.json"));
        Files.writeString(output.resolve("keep.txt"), "keep");
        assertThatThrownBy(() -> App.run(users, input, output, true)).isInstanceOf(IOException.class);
        assertThat(Files.readString(output.resolve("keep.txt"))).isEqualTo("keep");
        assertNoTemporaryFiles();
    }

    @Test
    void createsParentsAndProcessesFollowingCommandsAfterValidationErrors() throws Exception {
        Path input = Files.writeString(directory.resolve("commands.json"), """
                [
                  {"command":"unknown","username":"manager","timestamp":"2025-10-20"},
                  {"command":"viewTickets","username":"manager","timestamp":"2025-10-20"}
                ]
                """);
        Path output = directory.resolve("nested/results.json");
        App.run(users, input, output, false);
        var result = JSON.readTree(output.toFile());
        assertThat(result).hasSize(2);
        assertThat(result.get(0).has("error")).isTrue();
        assertThat(result.get(1).get("tickets")).isEmpty();
    }

    @Test
    void helpAndInvalidArgumentsHaveDistinctExitCodes() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout);
        PrintStream err = new PrintStream(stderr);
        assertThat(App.runCli(new String[]{"--help"}, out, err)).isZero();
        assertThat(stdout.toString()).contains("--users", "--input", "--output", "--overwrite");
        assertThat(stderr.toString()).isEmpty();
        assertThat(App.runCli(new String[]{"--input"}, out, err)).isEqualTo(2);
        assertThat(App.runCli(new String[]{"--users", "u", "--users", "v"}, out, err)).isEqualTo(2);
        assertThat(App.runCli(new String[]{"--overwrite", "--overwrite"}, out, err)).isEqualTo(2);
        assertThat(App.runCli(new String[]{"--wat"}, out, err)).isEqualTo(2);
    }

    @Test
    void missingInputReturnsFailureExitCode() {
        PrintStream sink = new PrintStream(new ByteArrayOutputStream());
        assertThat(App.runCli(new String[]{"--users", users.toString(), "--input", "missing.json",
                "--output", directory.resolve("out.json").toString()}, sink, sink)).isEqualTo(1);
    }

    void assertNoTemporaryFiles() throws Exception {
        try (var files = Files.list(directory)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .noneMatch(name -> name.startsWith(".bug-tracker-"));
        }
    }
}
