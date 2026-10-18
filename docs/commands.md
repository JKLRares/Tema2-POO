# Command reference

## Envelope

The command file contains an array. Each command has a textual `command`, `username` and ISO `timestamp` (`yyyy-MM-dd`). Parameters can be nested in `params` or supplied at the top level. A parameter must not occur in both locations. Unknown extra fields are tolerated for coursework compatibility; known fields must have the documented types.

```json
{
  "command": "assignTicket",
  "username": "fullstack_senior",
  "timestamp": "2025-10-20",
  "params": {"ticketID": 0}
}
```

IDs and capacities are nonnegative 32-bit integers. Dates use four-digit years and are simulated command dates, not readings of the host clock. The supplied catalog identifies reporters, developers and managers. Invalid syntax for the entire JSON file is a batch failure; an invalid command becomes an error response, and subsequent commands execute.

Successful mutation commands generally produce no response. Queries append JSON objects to the result array. Original commands retain their coursework field names, error wording and silent no-op behavior where the reference scenarios require it.

## Supported commands

| Command | Actor | Parameters and behavior |
| --- | --- | --- |
| `reportTicket` | Reporter | Ticket fields below; allowed during a 12-day testing phase |
| `createMilestone` | Manager | `name`, `dueDate`; optional `startDate`, `tickets`, `assignedDevs`, `blockingFor` |
| `assignTicket` | Eligible developer | `ticketID`; milestone must have started and be unblocked |
| `undoAssignTicket` | Assignee | `ticketID`; releases an in-progress assignment |
| `changeStatus` | Assignee | `ticketID`; advances one lifecycle step |
| `undoChangeStatus` | Assignee | `ticketID`; reverses resolution or closure |
| `addComment` | Assignee or original reporter | `ticketID`, `comment`; at least 10 characters; no anonymous tickets or reporter comments on closed tickets |
| `undoAddComment` | Comment author | `ticketID`; removes the author's latest comment |
| `viewTickets` | Known user | Managers see all tickets, reporters see their reports, developers see open tickets in their milestones |
| `viewAssignedTickets` | Developer | Lists owned tickets by descending priority, creation date and ID |
| `viewMilestones` | Manager or developer | Managers see milestones they created; developers see their milestones |
| `viewTicketHistory` | Manager or developer | Managers see their milestone tickets; developers see assigned/interacted tickets, with actions truncated at their latest release when no longer assigned |
| `viewNotifications` | Known user | Returns pending milestone notifications and drains the queue |
| `search` | Manager or developer | Optional `filters`; see below |
| `generateCustomerImpactReport` | Manager | Aggregates impact for open and in-progress tickets |
| `generateTicketRiskReport` | Manager | Aggregates ticket risk by type |
| `generateResolutionEfficiencyReport` | Manager | Considers resolved and closed tickets with assignment/resolution dates |
| `generatePerformanceReport` | Manager | Reports subordinate performance for the previous calendar month and stores those scores for developer search |
| `appStabilityReport` | Manager | Reports active-ticket risk, impact and stability classification |
| `startTestingPhase` | Manager | Starts a new testing window when no milestone has unresolved tickets |
| `lostInvestors` | Manager | Ends the current testing phase |
| `planAssignments` | Milestone creator | `milestone`; optional `capacityPerDeveloper`, default 1 |

## Ticket fields

`type`, `title` and `businessPriority` are required. `type` is `BUG`, `FEATURE_REQUEST` or `UI_FEEDBACK`; priority is `LOW`, `MEDIUM`, `HIGH` or `CRITICAL`.

`reportedBy` can identify an existing reporter, including reporting on behalf of another reporter. An empty or omitted reporter is allowed only for bugs; anonymous bugs start at `LOW` priority and do not accept comments. Optional `expertiseArea` values are `FRONTEND`, `BACKEND`, `DEVOPS`, `DESIGN`, `DB` and `FULLSTACK`. A ticket without an expertise area cannot pass assignment eligibility checks. `description` is optional.

| Type | Additional fields |
| --- | --- |
| `BUG` | `expectedBehavior`, `actualBehavior`, `environment`, integer `errorCode`; `frequency`: `RARE`, `OCCASIONAL`, `FREQUENT`, `ALWAYS`; `severity`: `MINOR`, `MODERATE`, `SEVERE` |
| `FEATURE_REQUEST` | `businessValue`: `S`, `M`, `L`, `XL`; `customerDemand`: `LOW`, `MEDIUM`, `HIGH`, `VERY_HIGH` |
| `UI_FEEDBACK` | `uiElementId`, `screenshotUrl`, `suggestedFix`, `businessValue`, integer `usabilityScore` from 1 through 10 |

Optional type-specific values retain the original metric defaults when absent. Strings, enums and numbers are checked when present; explicit `null` is not a substitute for an omitted optional parameter.

## Milestones

Names, ticket IDs, assigned developers and blocked target names must be unique within their relevant scope. Every referenced ticket/developer/blocked milestone must exist, and a ticket can belong to only one milestone. Empty milestone lists are allowed. All references are validated before any ticket history, notifications or dependency edges change.

`blockingFor` lists existing milestones that must wait for the new milestone. Late milestone creation is allowed, including a due date before creation. `startDate` defaults to the command timestamp.

Milestone completion means every ticket is `CLOSED`; `RESOLVED` still awaits closure. The displayed `completionPercentage` is a fraction from 0 to 1, preserving the original protocol. Date counters use the assignment's inclusive-day convention. Developer repartition is ordered by assigned-ticket count and then username.

## Search filters

`searchType` defaults to `TICKET`; `DEVELOPER` searches are available to managers over their subordinates.

Ticket filters include `type`, `businessPriority`, `createdAt`, exclusive `createdAfter`/`createdBefore`, and `availableForAssignment`. Managers can also filter `expertiseArea` and `keywords`. Keywords are a string or array of strings; matches are case-insensitive with locale-independent normalization. `matchingWords` contains complete matching words, rather than the query fragments.

Developer filters include `expertiseArea`, `seniority`, the date filters applied to `hireDate`, `keywords` over username/email, `performanceScoreAbove` and `performanceScoreBelow`. The lower score bound is inclusive and the upper bound is exclusive. Scores come from the most recently generated performance report, rather than being recomputed by search.

## Assignment plans

The response includes a `plan` object with `milestone`, `blocked`, `assignments` and `unplanned`. Assignment entries contain `ticketID`, `developer` and effective `priority`. Unplanned entries include `eligibleDevelopers` and one reason:

- `BLOCKED`: prerequisite tickets remain unclosed.
- `NOT_STARTED`: the request date precedes the milestone start.
- `NO_ELIGIBLE_DEVELOPER`: no milestone developer meets expertise/seniority rules at the projected priority.
- `CAPACITY`: eligible developers have no available capacity in the maximum matching.

Only currently open, unassigned tickets are candidates. Existing `IN_PROGRESS` and `RESOLVED` assignments anywhere in the session consume capacity; closed tickets do not. Zero capacity is valid. Planning projects deadline escalation but does not release current assignments, send notifications, change priorities or apply proposals.
