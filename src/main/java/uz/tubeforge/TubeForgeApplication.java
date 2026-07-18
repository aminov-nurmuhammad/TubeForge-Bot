package uz.tubeforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class TubeForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TubeForgeApplication.class, args);
    }
}
