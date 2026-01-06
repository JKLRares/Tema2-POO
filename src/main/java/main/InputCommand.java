package main;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InputCommand {
    @JsonProperty("command")
    public String command;

    @JsonProperty("username")
    public String username;

    @JsonProperty("timestamp")
    public String timestamp;

    @JsonProperty("params")
    public Map<String, Object> params = new HashMap<>();

    @JsonAnySetter
    public void setOtherField(String key, Object value) {
        this.params.put(key, value);
    }
}