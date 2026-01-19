package main.tickets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import main.enums.BusinessValue;
import main.enums.CustomerDemand;

@Getter
public final class FeatureRequestTicket extends Ticket {
    private static final double FEATURE_RISK_DIV = 20.0;
    private static final double FEATURE_EFFICIENCY_DIV = 20.0;
    private static final double ROUND = 100.0;

    @JsonIgnore
    private BusinessValue businessValue;
    @JsonIgnore private CustomerDemand customerDemand;

    FeatureRequestTicket() {
    }
    private FeatureRequestTicket(final FeatureReqBuilder builder) {
        super(builder);
        this.businessValue = builder.bizVal;
        this.customerDemand = builder.demand;
    }

    @Override
    public double calculateImpact() {
        double bv = getBusinessValue(this.businessValue);
        double demand = getCustomerDemandValue(this.customerDemand);
        return bv * demand;
    }

    @Override
    public double calculateRisk() {
        double bv = getBusinessValue(this.businessValue);
        double demand = getCustomerDemandValue(this.customerDemand);
        return ((bv + demand) / FEATURE_RISK_DIV) * ROUND;
    }

    @Override
    public double calculateEfficiency(final long days) {
        double bv = getBusinessValue(this.businessValue);
        double demand = getCustomerDemandValue(this.customerDemand);
        double rawScore = (bv + demand) / (double) days;
        return (rawScore * ROUND) / FEATURE_EFFICIENCY_DIV;
    }

    public final static class FeatureReqBuilder
            extends TicketBuilder<FeatureRequestTicket, FeatureReqBuilder> {
        private BusinessValue bizVal;
        private CustomerDemand demand;

        /**
         * Sets business value.
         * @param v val
         * @return builder
         */
        public FeatureReqBuilder setBusinessValue(final BusinessValue v) {
            this.bizVal = v;
            return this;
        }

        /**
         * Sets customer demand.
         * @param v val
         * @return builder
         */
        public FeatureReqBuilder setCustomerDemand(final CustomerDemand v) {
            this.demand = v;
            return this;
        }

        /**
         * Builds.
         * @return ticket
         */
        @Override public FeatureRequestTicket build() {
            return new FeatureRequestTicket(this);
        }

        /**
         * Self.
         * @return self
         */
        @Override protected FeatureReqBuilder self() {
            return this;
        }
    }
}
