package org.openkilda.functionaltests.extension

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

/**
 * Provides additional logging with spec/feature names and additional metadata info during the test run.
 */
@Slf4j
class TestDataLoggingExtension extends AbstractGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        spec.fixtureMethods*.addInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                log.debug "Running fixture: ${invocation.spec.name}#${invocation.method.name}"
                invocation.proceed()
            }
        })
        spec.allFeatures*.addIterationInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                log.info "Running test: ${invocation.spec.name}#${invocation.iteration.name}"
                invocation.proceed()
            }
        })
    }

}