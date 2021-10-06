package org.openkilda.functionaltests.listeners

import groovy.util.logging.Slf4j
import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.SpecInfo

@Slf4j
class LogParallelSpecsListener extends AbstractRunListener {
    static List<String> specsInProgress = []

    @Override
    void beforeSpec(SpecInfo spec) {
        specsInProgress << spec.displayName
    }

    @Override
    void afterSpec(SpecInfo spec) {
        specsInProgress.remove(spec.displayName)
    }

    @Override
    void error(ErrorInfo error) {
        log.error("Specs ran in parallel: $specsInProgress")
    }
}
