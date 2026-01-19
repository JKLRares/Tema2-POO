package main.commands;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public class InputCommand {
    @JsonProperty("command")
    private String command;

    @JsonProperty("username")
    private String username;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("params")
    private Map<String, Object> params = new HashMap<>();

    @JsonAnySetter
    public void setOtherField(final String key, final Object value) {
        this.params.put(key, value);
    }
}
