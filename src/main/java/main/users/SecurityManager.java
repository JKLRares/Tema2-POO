package main.users;

import java.util.Arrays;
import java.util.List;
import main.enums.Expertise;
import main.enums.Priority;
import main.enums.Seniority;
import main.enums.TicketType;
import main.tickets.Ticket;

/** Shared, stateless assignment policy used by commands, search and planning. */

public final class SecurityManager {
    private SecurityManager() {
    }

    /** Checks both expertise and seniority; an absent user is never eligible. */
    public static boolean hasAccess(final User user, final Ticket ticket) {
        return hasAccess(user, ticket, ticket.getPriority());
    }

    /** Checks both expertise and seniority; an absent user is never eligible. */
    public static boolean hasAccess(final User user, final Ticket ticket, final Priority priority) {
        DeveloperUser dev = user == null ? null : user.asDeveloper();
        return dev != null && hasExpertise(dev.getExpertise(), ticket.getCategory())
                && hasSeniority(dev.getSeniority(), ticket.getType(), priority);
    }

    /** Tests the supported cross-specialization rules; missing expertise is ineligible. */
    public static boolean hasExpertise(final Expertise developer, final Expertise category) {
        if (developer == null || category == null) {
            return false;
        }
        return developer == Expertise.FULLSTACK || developer == category
                || developer == Expertise.FRONTEND && category == Expertise.DESIGN
                || developer == Expertise.DESIGN && category == Expertise.FRONTEND
                || developer == Expertise.BACKEND && category == Expertise.DB;
    }

    /** Tests the priority and ticket-type restrictions for this seniority. */
    public static boolean hasSeniority(final Seniority seniority, final Ticket ticket) {
        return hasSeniority(seniority, ticket.getType(), ticket.getPriority());
    }

    private static boolean hasSeniority(final Seniority seniority, final TicketType type,
                                        final Priority priority) {
        if (seniority == null || priority == null) {
            return false;
        }
        return seniority == Seniority.SENIOR
                || seniority == Seniority.MID && priority != Priority.CRITICAL
                || seniority == Seniority.JUNIOR
                && (priority == Priority.LOW || priority == Priority.MEDIUM)
                && type != TicketType.FEATURE_REQUEST;
    }

    /** Lists eligible expertise names in deterministic order for error responses. */
    public static List<String> requiredExpertise(final Expertise category) {
        return Arrays.stream(Expertise.values()).filter(e -> hasExpertise(e, category))
                .map(Enum::name).sorted().toList();
    }

    /** Lists eligible seniority names in deterministic order for error responses. */
    public static List<String> requiredSeniority(final Ticket ticket) {
        return Arrays.stream(Seniority.values()).filter(s -> hasSeniority(s, ticket))
                .map(Enum::name).sorted().toList();
    }
}
