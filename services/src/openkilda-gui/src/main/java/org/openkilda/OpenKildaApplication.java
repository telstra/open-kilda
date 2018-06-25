package org.openkilda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.openkilda.config.FilterConfig;

/**
 * The Class OpenKildaApplication.
 *
 * @author Gaurav Chugh
 */

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@ComponentScan({"org.usermanagement", "org.openkilda"})
@Import({FilterConfig.class})
@EnableJpaRepositories({"org.usermanagement", "org.openkilda"})
@EntityScan({"org.usermanagement", "org.openkilda"})
@EnableAutoConfiguration
public class OpenKildaApplication extends SpringBootServletInitializer {

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.boot.web.support.SpringBootServletInitializer#configure(org.springframework
     * .boot.builder.SpringApplicationBuilder)
     */
    @Override
    protected SpringApplicationBuilder configure(final SpringApplicationBuilder application) {
        return application.sources(OpenKildaApplication.class);
    }

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(final String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        try {
            SpringApplication.run(OpenKildaApplication.class, args);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
