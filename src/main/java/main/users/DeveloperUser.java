package main.users;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import main.enums.Expertise;
import main.enums.Seniority;

import java.time.LocalDate;

@Getter @Setter
public class DeveloperUser extends User {
    @JsonProperty("seniority")
    private Seniority seniority;

    @JsonProperty("hireDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    @JsonProperty("expertiseArea")
    private Expertise expertise;

    private double performanceScore = 0.0;

    /**
     * Returns string representation.
     * @return string representation
     */
    @Override
    public String toString() {
        return super.toString() + " | Seniority: " + seniority + " | Area: " + expertise;
    }

    /**
     * Casts to DeveloperUser.
     * @return this
     */
    @Override
    public DeveloperUser asDeveloper() {
        return this;
    }
}
