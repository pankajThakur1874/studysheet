package net.tridha.studysheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudySheetApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudySheetApplication.class, args);
    }
}
