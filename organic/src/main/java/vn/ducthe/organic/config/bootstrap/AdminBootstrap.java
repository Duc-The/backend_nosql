package vn.ducthe.organic.config.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.dto.request.StaffRequests;
import vn.ducthe.organic.service.StaffService;

@Component
@ConditionalOnProperty(name = "app.staff.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final StaffService staff;
    private final String username;
    private final String password;
    private final String fullName;

    AdminBootstrap(StaffService staff, @Value("${app.staff.bootstrap.username:}") String username,
                   @Value("${app.staff.bootstrap.password:}") String password,
                   @Value("${app.staff.bootstrap.full-name:}") String fullName) {
        this.staff = staff;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (staff.bootstrap(username, password, new StaffRequests.Profile(fullName, null, null, "Chủ cửa hàng", null))) {
            log.info("Initial administrator account created");
        }
    }
}
