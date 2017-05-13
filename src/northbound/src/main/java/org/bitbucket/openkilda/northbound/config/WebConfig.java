package org.bitbucket.openkilda.northbound.config;

import org.bitbucket.openkilda.northbound.utils.ExecutionTimeInterceptor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * Object mapper bean.
     *
     * @return the ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);
        return mapper;
    }

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
