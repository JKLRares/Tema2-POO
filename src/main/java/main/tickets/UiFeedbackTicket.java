package main.tickets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import main.enums.BusinessValue;

@Getter
public final class UiFeedbackTicket extends Ticket {
    private static final double UI_RISK_MAX = 11.0;
    private static final double UI_EFFICIENCY_DIV = 20.0;
    private static final double ROUND = 100.0;

    @JsonIgnore
    private String uiElementId;
    @JsonIgnore private BusinessValue businessValue;
    @JsonIgnore private Integer usabilityScore;
    @JsonIgnore private String screenshotUrl;
    @JsonIgnore private String suggestedFix;

    UiFeedbackTicket() {
    }
    private UiFeedbackTicket(final UiFeedBuilder builder) {
        super(builder);
        this.uiElementId = builder.uiId;
        this.businessValue = builder.bizVal;
        this.usabilityScore = builder.score;
        this.screenshotUrl = builder.url;
        this.suggestedFix = builder.fix;
    }

    @Override
    public double calculateImpact() {
        double bv = getBusinessValue(this.businessValue);
        double usb = this.usabilityScore != null ? this.usabilityScore : 0;
        return bv * usb;
    }

    @Override
    public double calculateRisk() {
        double bv = getBusinessValue(this.businessValue);
        double score = this.usabilityScore != null ? this.usabilityScore : 0;
        return (UI_RISK_MAX - score) * bv;
    }

    @Override
    public double calculateEfficiency(final long days) {
        double bv = getBusinessValue(this.businessValue);
        double usb = this.usabilityScore != null ? this.usabilityScore : 0;
        double rawScore = (bv + usb) / (double) days;
        return (rawScore * ROUND) / UI_EFFICIENCY_DIV;
    }

    public final static class UiFeedBuilder extends TicketBuilder<UiFeedbackTicket, UiFeedBuilder> {
        private String uiId;
        private BusinessValue bizVal;
        private Integer score;
        private String url;
        private String fix;

        /**
         * Sets UI element ID.
         * @param v val
         * @return builder
         */
        public UiFeedBuilder setUiElementId(final String v) {
            this.uiId = v;
            return this;
        }

        /**
         * Sets business value.
         * @param v val
         * @return builder
         */
        public UiFeedBuilder setBusinessValue(final BusinessValue v) {
            this.bizVal = v;
            return this;
        }

        /**
         * Sets usability score.
         * @param v val
         * @return builder
         */
        public UiFeedBuilder setUsabilityScore(final Integer v) {
            this.score = v;
            return this;
        }

        /**
         * Sets screenshot url.
         * @param v val
         * @return builder
         */
        public UiFeedBuilder setScreenshotUrl(final String v) {
            this.url = v;
            return this;
        }

        /**
         * Sets suggested fix.
         * @param v val
         * @return builder
         */
        public UiFeedBuilder setSuggestedFix(final String v) {
            this.fix = v;
            return this;
        }

        /**
         * Builds the ticket.
         * @return ticket
         */
        @Override public UiFeedbackTicket build() {
            return new UiFeedbackTicket(this);
        }

        /**
         * Returns self.
         * @return self
         */
        @Override protected UiFeedBuilder self() {
            return this;
        }
    }
}
