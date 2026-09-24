package com.harness.expensecapture;

import com.harness.expensecapture.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ExpenseCaptureApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ExpenseCaptureApplication.class, args);
    }
}
