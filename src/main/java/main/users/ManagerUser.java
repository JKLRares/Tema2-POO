package main.users;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class ManagerUser extends User {
    @JsonProperty("hireDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    @JsonProperty("subordinates")
    private List<String> subordinates;

    /**
     * Casts to ManagerUser.
     * @return this
     */
    @Override
    public ManagerUser asManager() {
        return this;
    }
}
