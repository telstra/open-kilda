/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.northbound.config;

import org.openkilda.northbound.utils.ExecutionTimeInterceptor;
import org.openkilda.northbound.utils.ExtraAuthInterceptor;
import org.openkilda.northbound.utils.async.CompletableFutureReturnValueHandler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * The Web Application configuration.
 */
@Configuration
@EnableWebMvc
@PropertySource({"classpath:northbound.properties"})
public class WebConfig implements WebMvcConfigurer {

    @Value("${web.request.asyncTimeout}")
    private Long asyncTimeout;

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        CompletableFutureReturnValueHandler futureHandler = new CompletableFutureReturnValueHandler();
        handlers.add(0, futureHandler);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(asyncTimeout);
    }

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("v1/swagger-ui.html", "/swagger-ui.html");
        registry.addRedirectViewController("v2/swagger-ui.html", "/swagger-ui.html");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(timeExecutionInterceptor());
        registry.addInterceptor(extraAuthInterceptor());
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

    @Bean
    public ExtraAuthInterceptor extraAuthInterceptor() {
        return new ExtraAuthInterceptor();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        return new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor());
    }
}
