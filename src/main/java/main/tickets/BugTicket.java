package main.tickets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import main.enums.Frequency;
import main.enums.Severity;

@Getter
public final class BugTicket extends Ticket {
    private static final double BUG_IMPACT_DIV = 48.0;
    private static final double BUG_RISK_DIV = 12.0;
    private static final double BUG_EFFICIENCY_MUL = 10.0;
    private static final double BUG_EFFICIENCY_DIV = 70.0;
    private static final double ROUND = 100.0;

    @JsonIgnore
    private String expectedBehavior;
    @JsonIgnore private String actualBehavior;
    @JsonIgnore private Frequency frequency;
    @JsonIgnore private Severity severity;
    @JsonIgnore private String environment;
    @JsonIgnore private Integer errorCode;

    BugTicket() {
    }

    private BugTicket(final BugBuilder builder) {
        super(builder);
        requireType(main.enums.TicketType.BUG);
        this.expectedBehavior = builder.expected;
        this.actualBehavior = builder.actual;
        this.frequency = builder.freq;
        this.severity = builder.sev;
        this.environment = builder.env;
        this.errorCode = builder.code;
    }

    @Override
    public double calculateImpact() {
        double freq = getFrequencyValue(this.frequency);
        double prio = getPriorityValue(this.getPriority());
        double sev = getSeverityValue(this.severity);
        return (freq * prio * sev * ROUND) / BUG_IMPACT_DIV;
    }

    @Override
    public double calculateRisk() {
        double freq = getFrequencyValue(this.frequency);
        double sev = getSeverityValue(this.severity);
        return (freq * sev / BUG_RISK_DIV) * ROUND;
    }

    @Override
    public double calculateEfficiency(final long days) {
        if (days <= 0) {
            throw new IllegalArgumentException("Resolution duration must be positive");
        }
        double freq = getFrequencyValue(this.frequency);
        double sev = getSeverityValue(this.severity);
        double rawScore = (freq + sev) * BUG_EFFICIENCY_MUL / days;
        return (rawScore * ROUND) / BUG_EFFICIENCY_DIV;
    }

    public static final class BugBuilder extends TicketBuilder<BugTicket, BugBuilder> {
        private String expected;
        private String actual;
        private Frequency freq;
        private Severity sev;
        private String env;
        private Integer code;

        /**
         * Sets expected behavior.
         * @param v val
         * @return builder
         */
        public BugBuilder setExpected(final String v) {
            this.expected = v;
            return this;
        }

        /**
         * Sets actual behavior.
         * @param v val
         * @return builder
         */
        public BugBuilder setActual(final String v) {
            this.actual = v;
            return this;
        }

        /**
         * Sets frequency.
         * @param v val
         * @return builder
         */
        public BugBuilder setFrequency(final Frequency v) {
            this.freq = v;
            return this;
        }

        /**
         * Sets severity.
         * @param v val
         * @return builder
         */
        public BugBuilder setSeverity(final Severity v) {
            this.sev = v;
            return this;
        }

        /**
         * Sets environment.
         * @param v val
         * @return builder
         */
        public BugBuilder setEnvironment(final String v) {
            this.env = v;
            return this;
        }

        /**
         * Sets error code.
         * @param v val
         * @return builder
         */
        public BugBuilder setErrorCode(final Integer v) {
            this.code = v;
            return this;
        }

        @Override public BugTicket build() {
            return new BugTicket(this);
        }

        @Override protected BugBuilder self() {
            return this;
        }
    }
}
