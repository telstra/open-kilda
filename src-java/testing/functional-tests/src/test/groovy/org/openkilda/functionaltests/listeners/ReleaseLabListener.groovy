package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.BaseSpecification
import org.spockframework.runtime.model.SpecInfo

class ReleaseLabListener extends AbstractSpringListener {

    void afterSpec(SpecInfo runningSpec) {
        //these changes have been added as during EnvExtension notifyContextInitialized an error can be thrown
        // (ex.: topology has not deleted/deployed successfully)
        // as a result the rest of the listeners in the list have not been initialized and have no context
        if (BaseSpecification.threadLocalTopology.get()) {
            BaseSpecification.threadLocalTopology.get().returnToPool()
            BaseSpecification.threadLocalTopology.set(null)
        }
    }
}
