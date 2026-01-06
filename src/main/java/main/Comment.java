package main;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Comment {
    @JsonProperty("author")
    private String author;

    @JsonProperty("content")
    private String content;

    @JsonProperty("createdAt")
    private String createdAt;
}