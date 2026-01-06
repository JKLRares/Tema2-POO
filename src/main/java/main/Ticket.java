package main;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
enum Status { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
enum Expertise { FRONTEND, BACKEND, DEVOPS, DESIGN, DB, FULLSTACK }
enum Frequency { RARE, OCCASIONAL, FREQUENT, ALWAYS }
enum Severity { MINOR, MODERATE, SEVERE }
enum BusinessValue { S, M, L, XL }
enum CustomerDemand { LOW, MEDIUM, HIGH, VERY_HIGH }

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
@JsonIgnoreProperties(ignoreUnknown = true) @Getter @Setter
abstract class Ticket {
    @JsonProperty("id") protected Integer id;
    @JsonProperty("type") protected String type;
    @JsonProperty("title") protected String title;
    @JsonProperty("businessPriority") protected Priority businessPriority;
    @JsonProperty("status") protected Status status;

    @JsonProperty("createdAt") protected String createdAt = "";
    @JsonProperty("assignedAt") protected String assignedAt = "";
    @JsonProperty("solvedAt") protected String solvedAt = "";
    @JsonProperty("assignedTo") protected String assignedTo = "";
    @JsonProperty("reportedBy") protected String reportedBy = "";

    @JsonProperty("comments") protected List<Object> comments = new ArrayList<>();

    @JsonIgnore protected Expertise expertiseArea;
    @JsonIgnore protected String description;

    public Ticket() {}
    protected Ticket(TicketBuilder<?, ?> builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.title = builder.title;
        this.businessPriority = builder.priority;
        this.status = builder.status;
        this.expertiseArea = builder.expertise;
        this.description = builder.desc;
        this.reportedBy = builder.reporter != null ? builder.reporter : "";

        if (!"BUG".equals(builder.type) && (builder.reporter == null || builder.reporter.trim().isEmpty())) {
            throw new IllegalArgumentException("Doar tichetele de tip BUG pot fi anonime!");
        }
    }

    @JsonIgnore public TicketType getType() { return TicketType.valueOf(this.type); }
    @JsonIgnore public Priority getPriority() { return this.businessPriority; }
    @JsonIgnore public Expertise getCategory() { return this.expertiseArea; }
    public String getReportedBy() { return this.reportedBy; }

    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    abstract static class TicketBuilder<T extends Ticket, B extends TicketBuilder<T, B>> {
        protected Integer id;
        protected String type;
        protected String title;
        protected Priority priority;
        protected Status status;
        protected Expertise expertise;
        protected String desc;
        protected String reporter;

        public B withId(Integer v) { this.id = v; return self(); }
        public B withType(String v) { this.type = v; return self(); }
        public B withTitle(String v) { this.title = v; return self(); }
        public B withPriority(Priority v) { this.priority = v; return self(); }
        public B withStatus(Status v) { this.status = v; return self(); }
        public B withExpertise(Expertise v) { this.expertise = v; return self(); }
        public B withDescription(String v) { this.desc = v; return self(); }
        public B withReporter(String v) { this.reporter = v; return self(); }

        public abstract T build();
        protected abstract B self();
    }
}

class BugTicket extends Ticket {
    @JsonIgnore private String expectedBehavior;
    @JsonIgnore private String actualBehavior;
    @JsonIgnore private Frequency frequency;
    @JsonIgnore private Severity severity;
    @JsonIgnore private String environment;
    @JsonIgnore private Integer errorCode;

    public BugTicket() {}
    private BugTicket(Type1Builder builder) {
        super(builder);
        this.expectedBehavior = builder.expected;
        this.actualBehavior = builder.actual;
        this.frequency = builder.freq;
        this.severity = builder.sev;
        this.environment = builder.env;
        this.errorCode = builder.code;
    }
    public static class Type1Builder extends TicketBuilder<BugTicket, Type1Builder> {
        private String expected;
        private String actual;
        private Frequency freq;
        private Severity sev;
        private String env;
        private Integer code;
        public Type1Builder setExpected(String v) { this.expected = v; return this; }
        public Type1Builder setActual(String v) { this.actual = v; return this; }
        public Type1Builder setFrequency(Frequency v) { this.freq = v; return this; }
        public Type1Builder setSeverity(Severity v) { this.sev = v; return this; }
        public Type1Builder setEnvironment(String v) { this.env = v; return this; }
        public Type1Builder setErrorCode(Integer v) { this.code = v; return this; }
        @Override public BugTicket build() { return new BugTicket(this); }
        @Override protected Type1Builder self() { return this; }
    }
}

class FeatureRequestTicket extends Ticket {
    @JsonIgnore private BusinessValue businessValue;
    @JsonIgnore private CustomerDemand customerDemand;

    public FeatureRequestTicket() {}
    private FeatureRequestTicket(Type2Builder builder) {
        super(builder);
        this.businessValue = builder.bizVal;
        this.customerDemand = builder.demand;
    }
    public static class Type2Builder extends TicketBuilder<FeatureRequestTicket, Type2Builder> {
        private BusinessValue bizVal;
        private CustomerDemand demand;
        public Type2Builder setBusinessValue(BusinessValue v) { this.bizVal = v; return this; }
        public Type2Builder setCustomerDemand(CustomerDemand v) { this.demand = v; return this; }
        @Override public FeatureRequestTicket build() { return new FeatureRequestTicket(this); }
        @Override protected Type2Builder self() { return this; }
    }
}

class UiFeedbackTicket extends Ticket {
    @JsonIgnore private String uiElementId;
    @JsonIgnore private BusinessValue businessValue;
    @JsonIgnore private Integer usabilityScore;
    @JsonIgnore private String screenshotUrl;
    @JsonIgnore private String suggestedFix;

    public UiFeedbackTicket() {}
    private UiFeedbackTicket(Type3Builder builder) {
        super(builder);
        this.uiElementId = builder.uiId;
        this.businessValue = builder.bizVal;
        this.usabilityScore = builder.score;
        this.screenshotUrl = builder.url;
        this.suggestedFix = builder.fix;
    }

    public static class Type3Builder extends TicketBuilder<UiFeedbackTicket, Type3Builder> {
        private String uiId;
        private BusinessValue bizVal;
        private Integer score;
        private String url;
        private String fix;
        public Type3Builder setUiElementId(String v) { this.uiId = v; return this; }
        public Type3Builder setBusinessValue(BusinessValue v) { this.bizVal = v; return this; }
        public Type3Builder setUsabilityScore(Integer v) { this.score = v; return this; }
        public Type3Builder setScreenshotUrl(String v) { this.url = v; return this; }
        public Type3Builder setSuggestedFix(String v) { this.fix = v; return this; }
        @Override public UiFeedbackTicket build() { return new UiFeedbackTicket(this); }
        @Override protected Type3Builder self() { return this; }
    }
}