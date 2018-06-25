package org.openkilda.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import org.openkilda.auth.interceptor.RequestInterceptor;

@Configuration
public class RequestInterceptorConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private RequestInterceptor requestInterceptor;

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor);
    }
}

