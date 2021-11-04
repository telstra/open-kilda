package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.extension.spring.ContextAwareGlobalExtension

import org.spockframework.runtime.model.SpecInfo

class RegisterListenersExtension extends ContextAwareGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        //order matters. first added first executed
        spec.addListener(new LogParallelSpecsListener())
        spec.addListener(new CleanupVerifierListener())
        spec.addListener(new ReleaseLabListener())
    }
}
