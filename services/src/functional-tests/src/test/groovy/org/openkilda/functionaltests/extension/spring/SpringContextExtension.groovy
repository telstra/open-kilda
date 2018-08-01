package org.openkilda.functionaltests.extension.spring

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.AbstractMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

@Slf4j
class SpringContextExtension extends AbstractGlobalExtension implements ApplicationContextAware {
    private static ApplicationContext context;
    boolean initialized = false

    void visitSpec(SpecInfo specInfo) {
        //this is the earliest point where Spock can have access to Spring context
        specInfo.allFixtureMethods*.addInterceptor new AbstractMethodInterceptor() {
            @Override
            void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
                if (!initialized) {
                    context.getAutowireCapableBeanFactory().autowireBeanProperties(
                            invocation.sharedInstance, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
                    initialized = true
                    invocation.proceed()
                }
            }
        }
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.info("setting app spring context for spock extensions")
        context = applicationContext
    }
}
