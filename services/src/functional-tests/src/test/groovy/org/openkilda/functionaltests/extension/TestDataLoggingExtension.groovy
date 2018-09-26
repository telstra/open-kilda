package org.openkilda.functionaltests.extension

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

@Slf4j
class TestDataLoggingExtension extends AbstractGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        spec.addInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                log.info "Running spec: $spec.name"
                invocation.proceed()
            }
        })
        spec.fixtureMethods*.addInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                log.debug "Running fixture: $invocation.method.name"
                invocation.proceed()
            }
        })
        spec.allFeatures*.addIterationInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                log.info "Running test: ${invocation.iteration.name}"
                invocation.proceed()
            }
        })
    }

}