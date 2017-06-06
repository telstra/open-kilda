package org.bitbucket.openkilda.northbound.config;

import org.bitbucket.openkilda.northbound.utils.ExecutionTimeInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * The Web Application configuration.
 */
@Configuration
@EnableWebMvc
@ComponentScan({"org.bitbucket.openkilda.northbound.controller",
        "org.bitbucket.openkilda.northbound.model",
        "org.bitbucket.openkilda.northbound.service"})
@PropertySource({"classpath:northbound.properties"})
public class WebConfig extends WebMvcConfigurerAdapter {
    /**
     * {@inheritDoc}
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(timeExecutionInterceptor());
    }

    /**
     * Request processing time counting interceptor.
     *
     * @return interceptor instance
     */
    @Bean
    public ExecutionTimeInterceptor timeExecutionInterceptor() {
        return new ExecutionTimeInterceptor();
    }
}
