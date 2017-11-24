package org.openkilda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

/**
 * The Class OpenKildaApplication.
 *
 * @author Gaurav Chugh
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@ComponentScan("org.openkilda")
public class OpenKildaApplication extends SpringBootServletInitializer {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.boot.web.support.SpringBootServletInitializer#configure(org.springframework
     * .boot.builder.SpringApplicationBuilder)
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(OpenKildaApplication.class);
    }

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) throws Exception {
        SpringApplication.run(OpenKildaApplication.class, args);
    }
}
