/* Copyright 2022 Telstra Open Source
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

import org.openkilda.northbound.utils.async.CompletableFutureReturnValueHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;

/**
 * The Spring MVC RequestMapping configuration.
 */
@Configuration
public class WebRequestMappingConfig {
    @Value("${web.request.asyncTimeout}")
    private Long asyncTimeout;

    @Autowired
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    /**
     * Adds instance of {@link CompletableFutureReturnValueHandler} to the list of value handlers and put it on the
     * first place (thus we override default handler for completable future
     * {@link org.springframework.web.servlet.mvc.method.annotation.CompletionStageReturnValueHandler}).
     */
    @PostConstruct
    public void init() {
        CompletableFutureReturnValueHandler futureHandler = new CompletableFutureReturnValueHandler();
        List<HandlerMethodReturnValueHandler> defaultHandlers =
                new ArrayList<>(requestMappingHandlerAdapter.getReturnValueHandlers());
        defaultHandlers.add(0, futureHandler);

        requestMappingHandlerAdapter.setReturnValueHandlers(defaultHandlers);
        requestMappingHandlerAdapter.setAsyncRequestTimeout(asyncTimeout);
    }
}
