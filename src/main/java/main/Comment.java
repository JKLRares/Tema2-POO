package main;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public final class Comment {
    @JsonProperty("author")
    private final String author;

    @JsonProperty("content")
    private final String content;

    @JsonProperty("createdAt")
    private final String createdAt;
}
