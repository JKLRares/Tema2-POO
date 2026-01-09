package main;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

enum UserRole {
    REPORTER,
    DEVELOPER,
    MANAGER
}

enum Seniority {
    JUNIOR,
    MID,
    SENIOR
}


enum TicketType {
    BUG,
    UI_FEEDBACK,
    FEATURE_REQUEST
}


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
abstract class User implements NotificationObserver{
    @JsonProperty("username")
    protected String username;

    @JsonProperty("email")
    protected String email;

    @JsonProperty("role")
    protected UserRole role;

    @JsonProperty("notifications")
    protected List<String> notifications = new ArrayList<>();

    public User() {}

    protected User(UserBuilder<?, ?> builder) {
        this.username = builder.username;
        this.email = builder.email;
        this.role = builder.role;
    }

    @Override
    public void update(String message) {
        this.notifications.add(message);
    }

    abstract static class UserBuilder<T extends User, B extends UserBuilder<T, B>> {
        protected String username;
        protected String email;
        protected UserRole role;

        public B withUsername(String username) {
            this.username = username;
            return self();
        }

        public B withEmail(String email) {
            this.email = email;
            return self();
        }

        public B withRole(UserRole role) {
            this.role = role;
            return self();
        }

        protected abstract B self();
        public abstract T build();
    }

    @Override
    public String toString() {
        return "User: " + username + " [" + role + "]";
    }
}

class ReporterUser extends User {

    public ReporterUser() {}
    private ReporterUser(ReporterUserBuilder builder) {
        super(builder);
    }

    public static class ReporterUserBuilder extends UserBuilder<ReporterUser, ReporterUserBuilder> {
        public ReporterUserBuilder() {
            this.role = UserRole.REPORTER;
        }

        @Override
        protected ReporterUserBuilder self() { return this; }
        @Override
        public ReporterUser build() { return new ReporterUser(this); }
    }
}

@Getter
class DeveloperUser extends User {
    @JsonProperty("seniority")
    private Seniority seniority;

    @JsonProperty("hireDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    @JsonProperty("expertiseArea")
    private Expertise expertise;

    public DeveloperUser() {}
    private DeveloperUser(DeveloperBuilder builder) {
        super(builder);
        this.seniority = builder.seniority;
        this.hireDate = builder.hireDate;
        this.expertise = builder.expertise;
    }

    @Override
    public String toString() {
        return super.toString() + " | Seniority: " + seniority + " | Area: " + expertise;
    }

    public static class DeveloperBuilder extends UserBuilder<DeveloperUser, DeveloperBuilder> {
        private Seniority seniority;
        private LocalDate hireDate;
        private Expertise expertise;

        public DeveloperBuilder() {
            this.role = UserRole.DEVELOPER;
        }

        public DeveloperBuilder withSeniority(Seniority seniority) {
            this.seniority = seniority;
            return this;
        }

        public DeveloperBuilder withExpertise(Expertise expertise) {
            this.expertise = expertise;
            return this;
        }

        public DeveloperBuilder withHireDate(LocalDate hireDate) {
            this.hireDate = hireDate;
            return this;
        }

        @Override
        protected DeveloperBuilder self() { return this; }
        @Override
        public DeveloperUser build() { return new DeveloperUser(this); }
    }
}

class ManagerUser extends User {
    @JsonProperty("hireDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    @JsonProperty("subordinates")
    private List<String> subordinates;

    public ManagerUser() {}
    private ManagerUser(ManagerBuilder builder) {
        super(builder);
        this.hireDate = builder.hireDate;
        this.subordinates = builder.subordinates;
    }

    public static class ManagerBuilder extends UserBuilder<ManagerUser, ManagerBuilder> {
        private LocalDate hireDate;
        private List<String> subordinates = new ArrayList<>();

        public ManagerBuilder() {
            this.role = UserRole.MANAGER;
        }

        public ManagerBuilder withHireDate(LocalDate hireDate) {
            this.hireDate = hireDate;
            return this;
        }

        public ManagerBuilder withSubordinates(List<String> subordinates) {
            this.subordinates = subordinates;
            return this;
        }

        @Override
        protected ManagerBuilder self() { return this; }
        @Override
        public ManagerUser build() { return new ManagerUser(this); }
    }
}


class SecurityManager {
    private static SecurityManager instance;

    private SecurityManager() {}

    public static synchronized SecurityManager getInstance() {
        if (instance == null) {
            instance = new SecurityManager();
        }
        return instance;
    }

    public boolean hasAccess(User user, Ticket ticket) {
        if (user.getRole() != UserRole.DEVELOPER) {
            System.out.println("Access Denied: Only Developers can access this workflow.");
            return false;
        }

        DeveloperUser dev = (DeveloperUser) user;

        TicketType type = ticket.getType();
        Priority priority = ticket.getPriority();
        Expertise ticketCategory = ticket.getCategory();

        boolean seniorityCheckPassed = false;

        switch (dev.getSeniority()) {
            case JUNIOR:
                boolean isPriorityOkJr = (priority == Priority.LOW || priority == Priority.MEDIUM);
                boolean isTypeOkJr = (type == TicketType.BUG || type == TicketType.UI_FEEDBACK);

                if (isPriorityOkJr && isTypeOkJr) {
                    seniorityCheckPassed = true;
                }
                break;

            case MID:
                boolean isPriorityOkMid = (priority != Priority.CRITICAL);
                boolean isTypeOkMid = (type == TicketType.BUG || type == TicketType.UI_FEEDBACK || type == TicketType.FEATURE_REQUEST);

                if (isPriorityOkMid && isTypeOkMid) {
                    seniorityCheckPassed = true;
                }
                break;

            case SENIOR:
                seniorityCheckPassed = true;
                break;
        }

        if (!seniorityCheckPassed) {
            return false;
        }
        Expertise devExpertise = dev.getExpertise();

        switch (devExpertise) {
            case FULLSTACK:
                return (ticketCategory == Expertise.FRONTEND ||
                        ticketCategory == Expertise.BACKEND ||
                        ticketCategory == Expertise.DEVOPS ||
                        ticketCategory == Expertise.DESIGN ||
                        ticketCategory == Expertise.DB);

            case FRONTEND:
                return (ticketCategory == Expertise.FRONTEND || ticketCategory == Expertise.DESIGN);

            case BACKEND:
                return (ticketCategory == Expertise.BACKEND || ticketCategory == Expertise.DB);

            case DEVOPS:
                return (ticketCategory == Expertise.DEVOPS);

            case DESIGN:
                return (ticketCategory == Expertise.DESIGN || ticketCategory == Expertise.FRONTEND);

            case DB:
                return (ticketCategory == Expertise.DB);

            default:
                return false;
        }
    }
}