package main.tickets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import main.Comment;
import main.enums.BusinessValue;
import main.enums.CustomerDemand;
import main.enums.Expertise;
import main.enums.Frequency;
import main.enums.HistoryActionType;
import main.enums.Priority;
import main.enums.Severity;
import main.enums.Status;
import main.enums.TicketType;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BugTicket.class, name = "BUG"),
        @JsonSubTypes.Type(value = FeatureRequestTicket.class, name = "FEATURE_REQUEST"),
        @JsonSubTypes.Type(value = UiFeedbackTicket.class, name = "UI_FEEDBACK")
})
@JsonIgnoreProperties(ignoreUnknown = true) @Getter
public abstract class Ticket {
    private static final int PRIORITY_HIGH_VAL = 3;
    private static final int PRIORITY_CRITICAL_VAL = 4;
    private static final int FREQUENCY_FREQUENT_VAL = 3;
    private static final int FREQUENCY_ALWAYS_VAL = 4;
    private static final int SEVERITY_SEVERE_VAL = 3;
    private static final int BUSINESS_VALUE_M_VAL = 3;
    private static final int BUSINESS_VALUE_L_VAL = 6;
    private static final int BUSINESS_VALUE_XL_VAL = 10;
    private static final int DEMAND_MEDIUM_VAL = 3;
    private static final int DEMAND_HIGH_VAL = 6;
    private static final int DEMAND_VERY_HIGH_VAL = 10;

    @JsonProperty("id") private Integer id;
    @JsonProperty("type") private String type;
    @JsonProperty("title") private String title;
    @JsonProperty("businessPriority") private Priority businessPriority;
    @JsonProperty("status") private Status status;
    @JsonIgnore private List<TicketHistoryEntry> history = new ArrayList<>();
    @Setter @JsonProperty("createdAt") private String createdAt = "";
    @JsonProperty("assignedAt") private String assignedAt = "";
    @JsonProperty("solvedAt") private String solvedAt = "";
    @JsonProperty("assignedTo") private String assignedTo = "";
    @JsonIgnore private String resolutionDate = "";
    @JsonIgnore private String closedAt = "";
    @JsonProperty("reportedBy") private String reportedBy = "";

    @JsonProperty("comments") private List<Comment> comments = new ArrayList<>();
    @Setter @JsonIgnore private Expertise expertiseArea;
    @JsonIgnore private String description;

    /**
     * Calculates the impact of the ticket.
     * @return impact value
     */
    public abstract double calculateImpact();

    /**
     * Calculates the risk of the ticket.
     * @return risk value
     */
    public abstract double calculateRisk();

    /**
     * Calculates the resolution efficiency.
     * @param daysToResolve number of days taken to resolve
     * @return efficiency score
     */
    public abstract double calculateEfficiency(long daysToResolve);

    Ticket() {
    }

    protected Ticket(final TicketBuilder<?, ?> builder) {
        if (builder.type == null || builder.id == null || builder.id < 0 || builder.title == null
                || builder.title.isBlank()
                || builder.priority == null || builder.status != Status.OPEN) {
            throw new IllegalArgumentException(
                    "New tickets require an ID, title, priority and OPEN status");
        }
        TicketType.valueOf(builder.type);
        this.id = builder.id;
        this.type = builder.type;
        this.title = builder.title;
        this.businessPriority = builder.priority;
        this.status = builder.status;
        this.expertiseArea = builder.expertise;
        this.description = builder.desc;
        this.reportedBy = builder.reporter != null ? builder.reporter : "";

        if (!"BUG".equals(builder.type)
                && (builder.reporter == null || builder.reporter.trim().isEmpty())) {
            throw new IllegalArgumentException("Only BUG tickets can be anonymous");
        }
    }

    protected final void requireType(final TicketType expected) {
        if (getType() != expected) {
            throw new IllegalArgumentException("Builder type does not match ticket subtype");
        }
    }

    /** Raises priority without allowing later observations to lower it. */
    public final void increasePriority(final Priority priority) {
        if (priority.ordinal() > businessPriority.ordinal()) {
            businessPriority = priority;
        }
    }

    /**
     * Gets the ticket type enum.
     * @return the ticket type
     */
    @JsonIgnore public TicketType getType() {
        return TicketType.valueOf(this.type);
    }

    /**
     * Gets the priority.
     * @return the priority
     */
    @JsonIgnore public Priority getPriority() {
        return this.businessPriority;
    }

    /**
     * Gets the expertise category.
     * @return the expertise
     */
    @JsonIgnore public Expertise getCategory() {
        return this.expertiseArea;
    }

    abstract static class TicketBuilder<T extends Ticket, B extends TicketBuilder<T, B>> {
        private Integer id;
        private String type;
        private String title;
        private Priority priority;
        private Status status;
        private Expertise expertise;
        private String desc;
        private String reporter;

        public B withId(final Integer v) {
            this.id = v;
            return self();
        }

        public B withType(final String v) {
            this.type = v;
            return self();
        }

        public B withTitle(final String v) {
            this.title = v;
            return self();
        }

        public B withPriority(final Priority v) {
            this.priority = v;
            return self();
        }

        public B withStatus(final Status v) {
            this.status = v;
            return self();
        }

        public B withExpertise(final Expertise v) {
            this.expertise = v;
            return self();
        }

        public B withDescription(final String v) {
            this.desc = v;
            return self();
        }

        public B withReporter(final String v) {
            this.reporter = v;
            return self();
        }

        public abstract T build();
        protected abstract B self();
    }

    /**
     * Adds a history entry to the ticket.
     * @param entry the history entry
     */
    public final void addHistoryEntry(final TicketHistoryEntry entry) {
        this.history.add(entry);
    }

    public final List<TicketHistoryEntry> getHistory() {
        return List.copyOf(history);
    }

    public final List<Comment> getComments() {
        return List.copyOf(comments);
    }

    /** Appends an immutable comment after command-level permission checks. */
    public final void addComment(final Comment comment) {
        comments.add(comment);
    }

    /** Removes only the most recent comment by this author. */
    public final void undoComment(final String username) {
        for (int index = comments.size() - 1; index >= 0; index--) {
            if (comments.get(index).getAuthor().equals(username)) {
                comments.remove(index);
                return;
            }
        }
    }

    /** Assignment and its initial status transition form one domain operation. */
    public final void assignTo(final String username, final String timestamp) {
        if (status != Status.OPEN || username == null || username.isBlank()) {
            throw new IllegalStateException("Only OPEN tickets can be assigned to a developer");
        }
        assignedTo = username;
        assignedAt = timestamp;
        solvedAt = "";
        resolutionDate = "";
        history.add(new TicketHistoryEntry(HistoryActionType.ASSIGNED, timestamp, username, null));
        transitionTo(Status.IN_PROGRESS, username, timestamp);
    }

    /** Releases ownership and all timestamps belonging to the previous assignment. */
    public final void releaseAssignment(final String timestamp, final HistoryActionType action) {
        String formerAssignee = assignedTo;
        if (status == Status.CLOSED || formerAssignee.isEmpty()) {
            throw new IllegalStateException("Ticket has no active assignment");
        }
        TicketHistoryEntry entry = new TicketHistoryEntry(null, null, null,
                action == HistoryActionType.REMOVED_FROM_DEV ? "system" : formerAssignee,
                timestamp, action, null,
                action == HistoryActionType.REMOVED_FROM_DEV ? formerAssignee : null);
        history.add(entry);
        assignedTo = "";
        assignedAt = "";
        closedAt = "";
        solvedAt = "";
        resolutionDate = "";
        status = Status.OPEN;
    }

    /** Advances an active assignment once; CLOSED tickets are unchanged. */
    public final void advanceStatus(final String username, final String timestamp) {
        if (status == Status.IN_PROGRESS) {
            resolutionDate = timestamp;
            solvedAt = timestamp;
            transitionTo(Status.RESOLVED, username, timestamp);
        } else if (status == Status.RESOLVED) {
            closedAt = timestamp;
            transitionTo(Status.CLOSED, username, timestamp);
        }
    }

    /** Undo restores resolution metadata as well as the status. History remains append-only. */
    public final void undoStatus(final String username, final String timestamp) {
        if (status == Status.CLOSED) {
            closedAt = "";
            solvedAt = resolutionDate;
            transitionTo(Status.RESOLVED, username, timestamp);
        } else if (status == Status.RESOLVED) {
            solvedAt = "";
            resolutionDate = "";
            transitionTo(Status.IN_PROGRESS, username, timestamp);
        }
    }

    private void transitionTo(final Status next, final String username, final String timestamp) {
        TicketHistoryEntry entry = new TicketHistoryEntry(null, status, next, username,
                timestamp, HistoryActionType.STATUS_CHANGED, null, null);
        history.add(entry);
        status = next;
    }

    /**
     * Gets numeric value for priority.
     * @param p priority
     * @return numeric value
     */
    protected int getPriorityValue(final Priority p) {
        if (p == null) {
            return 0;
        }
        switch (p) {
            case LOW: return 1;
            case MEDIUM: return 2;
            case HIGH: return PRIORITY_HIGH_VAL;
            case CRITICAL: return PRIORITY_CRITICAL_VAL;
            default: return 0;
        }
    }

    /**
     * Gets numeric value for frequency.
     * @param f frequency
     * @return numeric value
     */
    protected int getFrequencyValue(final Frequency f) {
        if (f == null) {
            return 0;
        }
        switch (f) {
            case RARE: return 1;
            case OCCASIONAL: return 2;
            case FREQUENT: return FREQUENCY_FREQUENT_VAL;
            case ALWAYS: return FREQUENCY_ALWAYS_VAL;
            default: return 0;
        }
    }

    /**
     * Gets numeric value for severity.
     * @param s severity
     * @return numeric value
     */
    protected int getSeverityValue(final Severity s) {
        if (s == null) {
            return 0;
        }
        switch (s) {
            case MINOR: return 1;
            case MODERATE: return 2;
            case SEVERE: return SEVERITY_SEVERE_VAL;
            default: return 0;
        }
    }

    /**
     * Gets numeric value for business value.
     * @param bv business value
     * @return numeric value
     */
    protected int getBusinessValue(final BusinessValue bv) {
        if (bv == null) {
            return 0;
        }
        switch (bv) {
            case S: return 1;
            case M: return BUSINESS_VALUE_M_VAL;
            case L: return BUSINESS_VALUE_L_VAL;
            case XL: return BUSINESS_VALUE_XL_VAL;
            default: return 0;
        }
    }

    /**
     * Gets numeric value for customer demand.
     * @param cd customer demand
     * @return numeric value
     */
    protected int getCustomerDemandValue(final CustomerDemand cd) {
        if (cd == null) {
            return 0;
        }
        switch (cd) {
            case LOW: return 1;
            case MEDIUM: return DEMAND_MEDIUM_VAL;
            case HIGH: return DEMAND_HIGH_VAL;
            case VERY_HIGH: return DEMAND_VERY_HIGH_VAL;
            default: return 0;
        }
    }
}
