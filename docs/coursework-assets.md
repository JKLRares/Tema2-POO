# Coursework materials and publication

This project originated from the 2025 second Java Object-Oriented Programming assignment at POLITEHNICA Bucharest, Faculty of Automatic Control and Computers.

The initial repository commit contains the application starter, Maven configuration, user catalog, 19 command scenarios, their reference results and the university checker. Most domain and command implementations were added in subsequent coursework commits. Git history retains that provenance.

| Paths | Classification | Handling |
| --- | --- | --- |
| `src/main/java/` | Application implementation, including the original entry-point location | Review as production source |
| `src/test/java/portfolio/` | Regression, integration and generated-case tests | Independent tests, except the explicit reference-scenario adapter |
| `examples/` | Small synthetic catalog and executable demonstration | Used by independent tests and the packaged example |
| `input/`, `ref/` | Original university catalog, scenarios and expected outputs | Retained unchanged |
| Root-level classes in `src/test/java/` | Original university grading harness | Retained unchanged; opt-in with `-Dtest=TestRunner` |
| `src/test/resources/checkstyle/` | Original grading configuration | Retained unchanged |
| `target/`, `out/` | Build products, test reports and generated application results | Ignored; exclude from commits |
| `.idea/` | Local IDE settings | Ignored |
| Root ZIP archives | Local submission/backups that may contain Git history | Preserved locally and ignored |

No standalone assignment handout, redistribution license or academic disclosure policy is present in the repository. Existing hosting does not establish permission to redistribute university materials. Confirm the applicable course policy before publishing or relicensing the checker, fixtures, assignment-derived material or archives. The checker also contains contributor attribution identifiers; retain required attribution and review it before redistribution. No license has been invented for these files.

The default build and CI run the 19 reference scenarios, so they require `input/` and `ref/`. The `portfolio-only` Maven profile runs the independent tests and executable example without reading those university JSON assets. If these assets must be omitted from a distributable copy, use that profile in CI and retain a separate private compatibility check. The original checker sources are still compiled when present; omitting all grading code also requires removing its test-only dependencies if desired.

The preserved checker's point-summary formatter can print `Total: 0/100` despite successful JUnit results because its display-name parser does not match the scenario naming format. Use the Surefire test counts to assess execution; the original grading harness is retained unchanged.
