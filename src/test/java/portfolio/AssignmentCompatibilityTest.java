package portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import main.App;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class AssignmentCompatibilityTest {
    @TempDir Path temporary;

    static Stream<Path> scenarios() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("input"))) {
            var scenarios = files.filter(p -> p.getFileName().toString().startsWith("in_"))
                    .sorted().toList();
            assertThat(scenarios).hasSize(19);
            return scenarios.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void preservesOriginalReference(Path input) throws Exception {
        Path output = temporary.resolve("result.json");
        String reference = input.getFileName().toString().replaceFirst("in_", "ref_");
        App.run(input.toString(), output.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertThatJson(mapper.readTree(output.toFile()))
                .isEqualTo(mapper.readTree(Path.of("ref", reference).toFile()));
    }
}
