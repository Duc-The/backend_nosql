package vn.ducthe.organic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrganicApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrganicApplication.class, args);
    }
}
