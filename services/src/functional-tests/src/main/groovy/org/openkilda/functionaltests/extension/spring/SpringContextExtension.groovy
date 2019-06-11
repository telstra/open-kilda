package org.openkilda.functionaltests.extension.spring

import static org.openkilda.functionaltests.extension.ExtensionHelper.isFeatureSpecial

import org.openkilda.functionaltests.extension.tags.Tag
import org.openkilda.functionaltests.extension.tags.TagExtension

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

/**
 * This extension is responsible for handling spring context-related things.
 * 
 * Runs special 'dummy' test before spec to ensure context init for tests with 'where' blocks.
 * Can accept listeners that will be provided with ApplicationContext as soon as it is accessible.
 */
@Slf4j
class SpringContextExtension extends AbstractGlobalExtension implements ApplicationContextAware {
    public static ApplicationContext context;
    private static List<SpringContextListener> listeners = []

    void visitSpec(SpecInfo specInfo) {
        //include dummy test only if the first feature in spec is parameterized or if the first feature to run is 
        // profile-dependent. Dummy test lets Spring context to be initialized before running actual features to allow 
        // accessing context from 'where' block. 
        //it will always be the first in execution order, guaranteed by FeatureOrderExtension
        def nonSpecialFeatures = specInfo.allFeaturesInExecutionOrder.findAll { !isFeatureSpecial(it) }
        def isProfileDependent = TagExtension.collectAllTags(nonSpecialFeatures[0]).contains(Tag.VIRTUAL) ||
                TagExtension.collectAllTags(nonSpecialFeatures[0]).contains(Tag.HARDWARE)
        specInfo.getAllFeatures().find {
            it.featureMethod.getAnnotation(PrepareSpringContextDummy)
        }?.excluded = !(nonSpecialFeatures[0].parameterized || isProfileDependent) ||
                specInfo.getFeatures().every { it.excluded || it.skipped } as boolean

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
                if (invocation?.getFeature()?.featureMethod?.getAnnotation(PrepareSpringContextDummy)) {
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

    static void addListener(SpringContextListener listener) {
        listeners.add(listener)
        if(context) {
            listener.notifyContextInitialized(context)
        }
    }
}
