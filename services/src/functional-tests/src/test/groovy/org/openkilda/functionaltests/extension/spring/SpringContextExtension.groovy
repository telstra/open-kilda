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
    public static ApplicationContext context;
    public static List<SpringContextListener> listeners = []
    boolean initialized = false

    void visitSpec(SpecInfo specInfo) {
        specInfo.getAllFeatures().find {it.name == "Prepare spring context.."}?.excluded = initialized
        initialized = true
        //this is the earliest point where Spock can have access to Spring context
        specInfo.setupMethods*.addInterceptor new AbstractMethodInterceptor() {
            def autowired = false
            @Override
            void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
                if(!autowired) {
                    context.getAutowireCapableBeanFactory().autowireBeanProperties(invocation.sharedInstance,
                            AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
                    autowired = true
                }
                invocation.proceed()
            }
        }
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.info("setting app spring context for spock extensions")
        context = applicationContext
        listeners.each {
            it.notifyContextInitialized(applicationContext)
        }
    }
}
