package org.openkilda.functionaltests.extension.fixture

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
 * @see {@link SetupOnce}
 */
@Slf4j
class SetupOnceExtension extends AbstractGlobalExtension {
    void visitSpec(SpecInfo specInfo) {
        def setupRan = false
        specInfo.allFixtureMethods*.addInterceptor new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                if (!setupRan && invocation.method.kind == MethodKind.SETUP) {
                    def spec = invocation.sharedInstance
                    if (spec instanceof SetupOnce) {
                        log.debug "Running fixture: setupOnce"
                        spec.setupOnce()
                        setupRan = true
                    }
                }
                invocation.proceed()
            }
        }
    }
}
