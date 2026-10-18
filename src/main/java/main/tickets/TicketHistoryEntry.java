package main.tickets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import main.enums.HistoryActionType;
import main.enums.Status;

@Getter @AllArgsConstructor
public final class TicketHistoryEntry {
    @JsonProperty("milestone")
    private final String milestone;
    @JsonIgnore
    private final Status fromStatus;
    @JsonProperty("to")
    private final Status toStatus;
    @JsonProperty("by")
    private final String user;
    @JsonProperty("timestamp")
    private final String timestamp;
    @JsonProperty("action")
    private final HistoryActionType action;
    @JsonIgnore
    private final String details;
    @JsonIgnore
    private final String removedFrom;

    /** The wire format uses the same field for a status or a former assignee. */
    @JsonProperty("from")
    public Object getFrom() {
        return removedFrom != null ? removedFrom : fromStatus;
    }

    public TicketHistoryEntry(final HistoryActionType action, final String timestamp,
                              final String user, final String details) {
        this(null, null, null, user, timestamp, action, details, null);
    }
}
