package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition

import org.spockframework.runtime.model.SpecInfo
import org.springframework.beans.factory.annotation.Autowired

class ReleaseLabListener extends AbstractSpringListener {
    @Autowired
    TopologyDefinition topology

    void afterSpec(SpecInfo runningSpec) {
        if (context) {
            context.autowireCapableBeanFactory.autowireBean(this)
            BaseSpecification.threadLocalTopology.set(null)
            topology.returnToPool()
        }
    }
}
