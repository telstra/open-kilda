package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo
import org.springframework.beans.factory.annotation.Autowired

/**
 * Calls cleanup manager to automatically remove garbage after tests
 */
class AutomaticCleanupListener extends AbstractSpringListener {
    @Autowired
    CleanupManager cleanupManager


    @Override
    void afterIteration(IterationInfo iteration) {
        if (!isUnitTest()) {
            context.autowireCapableBeanFactory.autowireBean(this)
            cleanupManager.run()
        }
    }

    @Override
    void afterSpec(SpecInfo spec){
        if (!isUnitTest()) {
            context.autowireCapableBeanFactory.autowireBean(this)
            cleanupManager.run(CleanupAfter.CLASS)
        }
    }

    boolean isUnitTest() {
        return context == null
    }
}
