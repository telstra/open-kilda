package org.openkilda.auth.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import org.openkilda.auth.model.RequestContext;

@Component
public class ServerContext {

    @Autowired
    private ApplicationContext context;

    public RequestContext getRequestContext() {
        return context.getBean(RequestContext.class);
    }

    public <T> T getBean(final Class<T> beanClass) {
        return context.getBean(beanClass);
    }
}
