package org.openkilda.functionaltests.extension.spring

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.MethodKind
import org.spockframework.runtime.model.SpecInfo
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

@Slf4j
class SpringContextExtension extends AbstractGlobalExtension implements ApplicationContextAware {
    public static ApplicationContext context;
    public static List<SpringContextListener> listeners = []

    void visitSpec(SpecInfo specInfo) {
        //include dummy test only if there is a parametrized test in spec
        //dummy test lets Spring context to be initialized before running actual features to allow accessing context
        //from 'where' block
        //it will always be first in the execution order
        specInfo.getAllFeatures().find {
            it.featureMethod.getAnnotation(PreparesSpringContextDummy)
        }?.excluded = !specInfo.getFeatures().find { it.parameterized } as boolean

        specInfo.allFixtureMethods*.addInterceptor(new IMethodInterceptor() {
            boolean autowired = false

            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                //this is the earliest point where Spock can have access to Spring context
                if (!autowired && invocation.method.kind == MethodKind.SETUP) {
                    context.getAutowireCapableBeanFactory().autowireBeanProperties(
                            invocation.sharedInstance, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
                    autowired = true
                }
                //do not invoke any fixtures for the dummy test
                if (invocation?.getFeature()?.featureMethod?.getAnnotation(PreparesSpringContextDummy)) {
                    return
                }
                invocation.proceed()
            }
        })
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.debug("Setting app spring context for spock extensions")
        context = applicationContext
        listeners.each {
            it.notifyContextInitialized(applicationContext)
        }
    }
}
