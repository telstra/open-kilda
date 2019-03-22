package org.openkilda.functionaltests.extension

import groovy.util.logging.Slf4j
import org.junit.AssumptionViolatedException
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

@Slf4j
class SkippedTestsLogger extends AbstractGlobalExtension {
    @Override
    void visitSpec(SpecInfo spec) {
        spec.allFixtureMethods*.addInterceptor(new AssumptionInterceptor())
        spec.allFeatures*.getFeatureMethod()*.addInterceptor(new AssumptionInterceptor())
    }

    class AssumptionInterceptor implements IMethodInterceptor {
        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            try {
                invocation.proceed()
            } catch (AssumptionViolatedException t) {
                log.warn("Skipped test: ${invocation.feature.spec.name}#${invocation.iteration.name}\n" +
                        "Reason: ${t.message}")
                throw t
            }
        }
    }
}
