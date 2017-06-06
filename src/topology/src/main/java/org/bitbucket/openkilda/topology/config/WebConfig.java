package org.bitbucket.openkilda.topology.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * The Web Application configuration.
 */
@Configuration
@EnableWebMvc
@ComponentScan({"org.bitbucket.openkilda.topology.controller",
        "org.bitbucket.openkilda.topology.model",
        "org.bitbucket.openkilda.topology.service"})
@PropertySource({"classpath:topology.properties"})
public class WebConfig extends WebMvcConfigurerAdapter {
}
