package org.openkilda.functionaltests.extension.fixture

import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.AbstractMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

/**
 * Due to JUnit + Spring nature, it is impossible to use Autowired fields in setupSpec (or BeforeClass).
 * Thus, providing new method 'setupOnce' which will run once at the beginning of the spec and have access
 * to Spring context.
 * @see {@link SetupOnce}
 */
class SetupOnceExtension extends AbstractGlobalExtension {
    void visitSpec(SpecInfo specInfo) {
        def setupRan = false
        specInfo.setupMethods*.addInterceptor new AbstractMethodInterceptor() {
            @Override
            void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
                if (!setupRan) {
                    def spec = invocation.sharedInstance
                    if (spec instanceof SetupOnce) {
                        spec.setupOnce()
                        setupRan = true
                    }
                }
                invocation.proceed()
            }
        }
    }
}
