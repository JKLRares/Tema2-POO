package main.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.Setter;
import main.notifications.NotificationObserver;
import main.enums.UserRole;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "role",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReporterUser.class, name = "REPORTER"),
        @JsonSubTypes.Type(value = DeveloperUser.class, name = "DEVELOPER"),
        @JsonSubTypes.Type(value = ManagerUser.class, name = "MANAGER")
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
public abstract class User implements NotificationObserver {
    @JsonProperty("username")
    private String username;

    @JsonProperty("email")
    private String email;

    @JsonProperty("role")
    private UserRole role;

    @JsonProperty("notifications")
    @Setter(AccessLevel.NONE)
    private List<String> notifications = new ArrayList<>();

    User() { }

    /**
     * Updates the user with a notification.
     * @param message the notification message
     */
    @Override
    public void update(final String message) {
        this.notifications.add(message);
    }

    public final List<String> getNotifications() {
        return List.copyOf(notifications);
    }

    /** Returns pending notifications as a snapshot and empties the queue. */
    public final List<String> drainNotifications() {
        List<String> pending = List.copyOf(notifications);
        notifications.clear();
        return pending;
    }

    /**
     * Returns string representation.
     * @return string representation
     */
    @Override
    public String toString() {
        return "User: " + username + " [" + role + "]";
    }

    /**
     * Casts to DeveloperUser.
     * @return DeveloperUser or null
     */
    public DeveloperUser asDeveloper() {
        return null;
    }

    /**
     * Casts to ManagerUser.
     * @return ManagerUser or null
     */
    public ManagerUser asManager() {
        return null;
    }
}
