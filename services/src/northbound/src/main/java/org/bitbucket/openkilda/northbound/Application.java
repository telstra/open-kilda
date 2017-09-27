package org.bitbucket.openkilda.northbound;

import org.bitbucket.openkilda.northbound.config.AppConfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Application.
 */
@EnableAutoConfiguration
@SpringBootApplication
public class Application {
    /**
     * Main method to start the application.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AppConfig.class, args);
    }
}
