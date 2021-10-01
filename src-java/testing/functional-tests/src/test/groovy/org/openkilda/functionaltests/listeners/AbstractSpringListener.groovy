package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.extension.spring.SpringContextNotifier

import org.spockframework.runtime.AbstractRunListener
import org.springframework.context.ApplicationContext

abstract class AbstractSpringListener extends AbstractRunListener implements SpringContextListener {
    { SpringContextNotifier.addListener(this) }

    ApplicationContext context

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        applicationContext.autowireCapableBeanFactory.autowireBean(this)
        context = applicationContext
    }
}
