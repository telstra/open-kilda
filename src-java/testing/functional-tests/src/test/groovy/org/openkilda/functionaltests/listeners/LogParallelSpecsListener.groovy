package org.openkilda.functionaltests.listeners

import groovy.util.logging.Slf4j
import org.apache.logging.log4j.ThreadContext
import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo

@Slf4j
class LogParallelSpecsListener extends AbstractRunListener {
    static List<String> specsInProgress = Collections.synchronizedList(new ArrayList<>())
    static final String TEST_KEY = "test_name"

    @Override
    void beforeSpec(SpecInfo spec) {
        ThreadContext.put(TEST_KEY, getCommonSpecPath(spec))
        specsInProgress << spec.displayName
    }

    @Override
    void beforeIteration(IterationInfo iteration) {
        ThreadContext.put(TEST_KEY, getIterationPath(iteration))
        log.info "Running test: ${iteration.feature.spec.nameTagged}#${iteration.nameTagged}"
    }

    @Override
    void afterIteration(IterationInfo iteration) {
        ThreadContext.put(TEST_KEY, getCommonSpecPath(iteration.feature.spec))
    }

    @Override
    void afterSpec(SpecInfo spec) {
        ThreadContext.remove(TEST_KEY)
        specsInProgress.remove(spec.displayName)
    }

    @Override
    void error(ErrorInfo error) {
        log.error("Specs ran in parallel: $specsInProgress")
    }

    String getSpecPath(SpecInfo spec) {
        return spec.reflection.name[spec.reflection.name.indexOf(".spec.") + 1..-1].replaceAll("\\.", "/") + "/"
    }

    String getIterationPath(IterationInfo iteration) {
        return getSpecPath(iteration.feature.spec) + iteration.displayName.replaceAll("/", "-")
    }

    String getCommonSpecPath(SpecInfo spec) {
        return getSpecPath(spec) + "common"
    }
}
