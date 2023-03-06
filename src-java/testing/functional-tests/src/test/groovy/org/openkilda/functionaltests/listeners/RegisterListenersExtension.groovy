package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.extension.spring.ContextAwareGlobalExtension

import org.spockframework.runtime.model.SpecInfo

class RegisterListenersExtension extends ContextAwareGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        //order matters. first added first executed
        [new DoCleanupListener(), new CleanupVerifierListener(), new LogParallelSpecsListener(),
         new ReleaseLabListener()].each {
            spec.addListener(it)
        }
    }
}
