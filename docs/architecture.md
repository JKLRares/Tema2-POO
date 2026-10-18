# Technical decisions

## Session ownership

`TrackerSession` constructs a fresh `Database` and fresh user entities from a JSON catalog. The command registry contains stateless handlers; repositories and notifications belong to the session. Responses are JSON snapshots, so editing a returned response cannot change a ticket.

`Database` uses insertion-ordered maps keyed by username, ticket ID and milestone name. Ticket-to-milestone membership has a separate index. Entity lookups are expected O(1); sorted reports still pay for their explicit ordering. Collection access does not expose repository membership for mutation. IDs are nonnegative 32-bit integers; the allocator uses a wider counter to represent exhaustion after the last valid ID without wrapping.

Milestone names are unique. Ticket membership, developers and ownership are fixed when a milestone is registered. Creation validates every ticket, developer and blocked milestone before publishing the aggregate, updating dependent milestones or appending ticket history. `blockingFor` targets must already exist: a new milestone can block existing milestones, so the command protocol builds an acyclic dependency graph without forward references.

## Ticket lifecycle

Assignment moves an `OPEN` ticket to `IN_PROGRESS` and records assignment and status actions. The assignee can advance it to `RESOLVED` and then `CLOSED`. Undo reverses these status transitions; assignment undo is available only in `IN_PROGRESS`.

`solvedAt` is the current resolution date. Closure is stored separately for milestone completion statistics. Reopening an assignment clears stale resolution metadata, while immutable history entries retain the sequence of events. Closed-ticket status changes are idempotent.

The stateless assignment policy is shared by assignment, search, priority-based revocation and planning. Juniors can take low/medium bugs or UI feedback; mid-level developers cannot take critical tickets. Expertise compatibility is checked separately.

Milestone observation raises effective priority every three elapsed days, saturating at `CRITICAL`. Unclosed tickets become critical one day before the deadline and remain critical when overdue. Dependency blocking pauses observed priority progression. Date arithmetic stays in `long`, including projections across extreme Java date ranges, and effective priority never decreases because of a backdated query.

Observations happen when viewing tickets, milestones or notifications, and when assigning or advancing ticket status. These operations can release an ineligible active assignment and append `REMOVED_FROM_DEV`, retaining the former developer in the coursework `from` field. A rejected user action can therefore still follow a valid time-driven escalation. Other commands retain their original observation behavior.

## Assignment planning

`planAssignments` is available to the manager who created the milestone. It projects open-ticket priorities at the request date without changing them, applies the shared eligibility policy, and counts existing in-progress or resolved work across the session against each developer's capacity.

Candidates are ordered by descending effective priority, creation date and ID; developers are ordered by username. For each candidate, breadth-first search finds an augmenting path through proposed assignments. Reversing that path can move an earlier proposal to a different developer without dropping a previously matched ticket.

The resulting matching maximizes the number of assignments. Among maximum-cardinality solutions, earlier candidate tickets are preferred. Developer tie-breaking is deterministic, but there is no additional objective to balance load, minimize cost or reserve specialist capacity beyond what matching requires.

Let T be open candidates, D milestone developers, N session tickets and E eligible ticket/developer pairs. Workload counting costs O(DN), and building eligibility checks T × D pairs. Sorting candidates projects priorities and inspects milestone prerequisites. The matching stage costs O(T(E + T + D)) with O(E + T + D) auxiliary space. Capacity is stored as a number, so a large capacity does not allocate a corresponding number of slots. An iterative search avoids recursion depth dependence.

Unplanned tickets distinguish blocked milestones, future start dates, missing eligible developers and exhausted capacity. Plans are advisory snapshots and should be recomputed after state changes.

## JSON and file boundaries

The batch adapter parses complete JSON documents with duplicate-key and trailing-token checks. Commands accept parameters either inside `params` or as top-level fields; providing the same parameter in both locations is rejected. The validator checks exact types, enum values, four-digit date syntax and numeric bounds before dispatch. Limiting protocol years preserves chronological string ordering and prevents previous-month arithmetic from leaving the supported Java date range. Only expected validation exceptions become command errors; unexpected execution defects propagate and prevent output publication.

The catalog uses explicitly named Jackson subtypes. No unrestricted polymorphic class loading or default typing is enabled. Each batch is local and trusted; it is not a network service or authentication boundary.

The CLI requires explicit input, catalog and output paths. Output replacement requires `--overwrite`. Replacements are prepared in the destination directory and use atomic move; unsupported filesystems fail without a non-atomic replacement fallback. Normal creation uses a non-replacing move. Temporary files are removed after publication failure. Input/output alias checks also cover symbolic and hard links to existing inputs.

An entire batch is not a database transaction: valid earlier commands remain in the session if a later command is rejected. Failed milestone creation and malformed requests do not publish partial domain objects. Output publication is separate from command execution.

## Build compatibility

The project retains Java 25 and its existing dependency versions. Surefire is explicitly configured for the JUnit Platform, and packaging produces a runnable JAR. The original Git-history grading test is opt-in because a source archive or a shallow checkout need not contain the student's development history. The default compatibility test runs all original scenarios independently of that grading metadata.
