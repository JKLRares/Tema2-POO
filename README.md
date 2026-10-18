# Bug Tracker

# Rares Constantinescu

A Java ticket tracker for coordinating bug fixes, feature requests and UI feedback across development milestones. It processes JSON command batches and produces JSON results, with role permissions, reversible ticket workflows and capacity-aware assignment planning.

## Overview

Reporters submit tickets, managers organize milestones and developers take ownership of eligible work. Milestone dependencies block assignment until prerequisite tickets are closed. Priorities increase as deadlines approach, and assignments that no longer meet the seniority rules are released with an audit entry.

The project began as the second Object-Oriented Programming assignment at POLITEHNICA Bucharest, Faculty of Automatic Control and Computers. The original starter code, checker and reference scenarios are retained; see [coursework materials](docs/coursework-assets.md) for provenance and publication considerations.

## Features

- Three ticket types with polymorphic impact, risk and resolution-efficiency calculations.
- Reporter, developer and manager workflows, with expertise and seniority checks.
- Dependent milestones, deadline escalation and notifications that are drained when viewed.
- Assignment undo, reversible status transitions, comments and ticket history.
- Ticket and developer search, stability reports and monthly performance reports.
- Read-only assignment plans that maximize eligible assignments under developer capacities.
- Isolated sessions, validated commands and protected output-file publication.

## Technologies

Java 25, Maven, Jackson 2.20 and Lombok for model boilerplate. Tests use JUnit Jupiter 5.11, AssertJ and JSON Unit. The preserved coursework checker also uses Checkstyle and JGit. Dependency and build-plugin versions are pinned in [pom.xml](pom.xml).

Java 25 is the repository's existing target; modernization does not require a Java upgrade.

## Architecture

| Component | Responsibility |
| --- | --- |
| `App` | CLI arguments, batch-file parsing and output publication |
| `TrackerSession` | One isolated session, command validation and dispatch |
| `commands` | Permissions, domain operations and compatible JSON responses |
| `Database` | In-memory indexes for users, tickets and milestone membership |
| `Ticket` and its subtypes | Lifecycle invariants, immutable history entries and type-specific metrics |
| `Milestone` | Dependencies and effective ticket priorities |
| `AssignmentPlanner` | Capacity-constrained matching without domain mutations |
| `SecurityManager` | Shared, stateless expertise and seniority policy |

No process-wide database or notification singleton is used. Sessions can be interleaved without sharing tickets, identifiers or notification queues. Each session is intended for sequential use.

See [technical decisions](docs/architecture.md) and the [command reference](docs/commands.md).

## Build

Install JDK 25 and Maven 3.6.3 or newer, then run from the repository root:

```bash
mvn -B --no-transfer-progress clean verify
```

This runs the regression suite and all 19 original JSON reference scenarios, then creates `target/bug-tracker.jar` with its runtime dependencies. IntelliJ configuration and prebuilt classes are not required.

## Run

```bash
java -jar target/bug-tracker.jar \
  --users examples/users.json \
  --input examples/commands.json \
  --output target/example-output.json
```

Use `--overwrite` on subsequent runs to replace an existing result. Input files are protected even when that flag is supplied. `--help` prints the CLI syntax.

A malformed JSON file or file-system failure returns exit code 1 without publishing replacement results; invalid CLI syntax returns 2. A processed batch returns 0, including batches with individual command errors. Those errors appear in the result array, and following commands continue executing.

## Example: planning a release

The included example reports three tickets, creates a milestone and requests a plan:

```json
{
  "command": "planAssignments",
  "username": "manager",
  "timestamp": "2025-10-20",
  "params": {
    "milestone": "Checkout release",
    "capacityPerDeveloper": 1
  }
}
```

The plan assigns the critical backend bug to `fullstack_senior` and the UI ticket to `frontend_mid`. The backend feature request is marked `CAPACITY`: the junior backend developer cannot take feature requests, and the eligible senior has no remaining slot.

Planning preserves tickets, histories and notifications. The example then executes explicit `assignTicket` commands and advances the backend bug through `IN_PROGRESS`, `RESOLVED` and `CLOSED`.

## Testing

```bash
mvn test                                    # Regression tests and original reference scenarios
mvn -Pportfolio-only clean verify           # Tests independent of university JSON fixtures
mvn -Dtest=AssignmentPlannerTest test        # Matching examples and exhaustive differential checks
mvn -Dtest=TestRunner test                   # Preserved university checker; requires Git history
```

Verification includes malformed-input and file-publication tests, isolated-session tests, lifecycle state-machine comparisons over 3,000 generated operations, and 300 generated assignment problems checked against an independent exhaustive solver. Original expected outputs are compared without rewriting them.

GitHub Actions builds on JDK 25, runs the complete default suite and exercises the packaged example.

## Technical decisions

- **Validate before mutation:** malformed parameter types and invalid references are rejected before a handler publishes a new milestone or ticket. Unknown commands produce visible errors.
- **Keep resolution and closure distinct:** `solvedAt` records resolution; a separate internal closure date drives milestone completion statistics. Undo restores both lifecycle state and timestamps.
- **Use matching for planning:** augmenting paths can move earlier proposals to other eligible developers. The result has maximum cardinality, with preference for higher-priority tickets when capacity is scarce.
- **Protect existing files:** replacement results are written to a temporary file beside the destination and moved atomically. Filesystems without atomic replacement support return an error.

## Limitations

- State is in memory for one batch/session; result JSON is a report, not a restorable database snapshot.
- Usernames and the user catalog are trusted local input. Role checks are not authentication.
- Deadline updates occur at command observation points, without a background scheduler. Backdated commands do not undo already increased priorities.
- Developer search exposes the score from the most recently generated performance report, preserving the coursework behavior.
- Planning counts `IN_PROGRESS` and `RESOLVED` tickets across milestones against capacity. Capacity is a planning constraint; manual assignments retain the original permission rules. Plans do not predict completion times or apply themselves.
- Coursework metrics and thresholds are simulation rules, not calibrated business forecasts.
