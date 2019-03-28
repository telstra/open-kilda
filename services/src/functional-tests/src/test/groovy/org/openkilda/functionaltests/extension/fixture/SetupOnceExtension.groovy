package org.openkilda.functionaltests.extension.fixture

import static org.openkilda.functionaltests.extension.ExtensionHelper.isFeatureSpecial

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.MethodKind
import org.spockframework.runtime.model.SpecInfo

/**
 * Due to JUnit + Spring nature, it is impossible to use Autowired fields in setupSpec (or BeforeClass).
 * Thus, providing new method 'setupOnce' which will run once at the beginning of the spec and have access
 * to Spring context.
 * Target spec should implement SetupOnce interface.
 * @see {@link SetupOnce}
 */
@Slf4j
class SetupOnceExtension extends AbstractGlobalExtension {
    void visitSpec(SpecInfo specInfo) {
        def setupRan = false
        //if there is a 'setup' method, run 'setupOnce' before 'setup'
        specInfo.fixtureMethods*.addInterceptor new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                if (!setupRan && invocation.method.kind == MethodKind.SETUP) {
                    setupRan = runSetupOnce(invocation)
                }
                invocation.proceed()
            }
        }

        //if there is no 'setup', run 'setupOnce' right before the first feature
        specInfo.allFeaturesInExecutionOrder.find { !isFeatureSpecial(it) }.featureMethod
                .addInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                if (!setupRan) {
                    setupRan = runSetupOnce(invocation)
                }
                invocation.proceed()
            }
        })
    }

    /**
     * @return whether setupOnce has been run
     */
    static boolean runSetupOnce(IMethodInvocation invocation) {
        def spec = invocation.sharedInstance
        if (spec instanceof SetupOnce) {
            log.debug "Running fixture: setupOnce"
            spec.setupOnce()
            return true
        }
        return false
    }
}
