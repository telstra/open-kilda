package org.bitbucket.openkilda.topology;

import org.bitbucket.openkilda.topology.config.AppConfig;

import org.springframework.boot.SpringApplication;

/**
 * The Application.
 */
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
