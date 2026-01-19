package main.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum HistoryActionType {
    ASSIGNED,
    @JsonProperty("DE-ASSIGNED") DE_ASSIGNED,
    STATUS_CHANGED,
    ADDED_TO_MILESTONE
}
