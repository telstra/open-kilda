package org.openkilda.functionaltests.extension.fixture

import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.AbstractMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo

/**
 * Allows to run granular setup/cleanup only once before/after all iterations of test's 'where' sequence.
 * @see {@link TestFixture}
 */
class TestFixtureExtension extends AbstractAnnotationDrivenExtension<TestFixture> {

    @Override
    void visitFeatureAnnotation(TestFixture annotation, FeatureInfo feature) {
        feature.featureMethod.addInterceptor(new AbstractMethodInterceptor() {
            boolean didRun = false
            boolean setupFailed = false

            @Override
            void interceptFeatureMethod(IMethodInvocation invocation) throws Throwable {
                def currentlyRunningSpec = invocation.sharedInstance
                if (!didRun) {
                    didRun = true
                    try {
                        currentlyRunningSpec."${annotation.setup()}"()
                    } catch (e) {
                        setupFailed = true
                        throw e
                    }
                }
                if (!setupFailed) {
                    invocation.proceed()
                }
            }
        })
        feature.addInterceptor(new AbstractMethodInterceptor() {
            @Override
            void interceptFeatureExecution(IMethodInvocation invocation) throws Throwable {
                def currentlyRunningSpec = invocation.sharedInstance
                try {
                    invocation.proceed()
                } finally {
                    currentlyRunningSpec."${annotation.cleanup()}"()
                }
            }
        })
    }
}
