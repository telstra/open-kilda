package org.openkilda.functionaltests.extension.failfast

import org.junit.AssumptionViolatedException
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

/**
 * This implements our Failfast policy: ensures that execution is stopped after first test failure.
 */
class FailFastExtension extends AbstractGlobalExtension {
    
    String failedTest

    @Override
    void visitSpec(SpecInfo spec) {
        spec.allFeatures*.addInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                //check before every feature. Will not unroll the test in case of failure to reduce the amount
                //of failed tests output
                checkForFailedTest()
                invocation.proceed()
            }
        })
        spec.allFeatures*.getFeatureMethod()*.addInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                //check before every iteration in order to stop further execution if test has multiple iterations(Unroll)
                checkForFailedTest()
                try {
                    invocation.proceed()
                } catch(Throwable t) {
                    if(!(t in AssumptionViolatedException)) {
                        failedTest = invocation.iteration.name
                    }
                    throw t
                }
            }
        })    
    }

    void checkForFailedTest() {
        if(failedTest) {
            throw new PreviousTestFailedError("Unable to run until '$failedTest' is fixed")
        }
    }
}
