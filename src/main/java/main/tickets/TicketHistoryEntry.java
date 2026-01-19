package main.tickets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import main.enums.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TicketHistoryEntry {
    @JsonProperty("milestone")
    private String milestone;

    @JsonProperty("from")
    private Status fromStatus;

    @JsonProperty("to")
    private Status toStatus;

    @JsonProperty("by")
    private String user;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("action")
    private HistoryActionType action;

    @JsonIgnore
    private String details;

    public TicketHistoryEntry(final HistoryActionType action, final String timestamp,
                              final String user, final String details) {
        this.action = action;
        this.timestamp = timestamp;
        this.user = user;
        this.details = details;
    }
}
