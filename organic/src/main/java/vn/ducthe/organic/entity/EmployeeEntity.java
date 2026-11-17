package vn.ducthe.organic.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@Document(collection = "employees")
public class EmployeeEntity extends BaseEntity {

    @Indexed(unique = true)
    private String accountId;
    @Indexed(unique = true)
    private String employeeCode;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String jobTitle;
    private String workStatus;
    private LocalDate hiredOn;
}
