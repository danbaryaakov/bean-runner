package org.beanrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BeanRunnerTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(BeanRunnerTestApplication.class, args);
    }
}
