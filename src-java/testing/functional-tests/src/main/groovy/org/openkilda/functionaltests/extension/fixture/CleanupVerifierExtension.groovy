package org.openkilda.functionaltests.extension.fixture

import org.openkilda.functionaltests.extension.spring.ContextAwareGlobalExtension
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.Constants
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

/**
 * Performs certain checks after every spec, tries to verify that environment is left clean.
 * This extension is meant to help to ensure that your tests have 'good' cleanups. Note that is does not guarantee a
 * fully clean env and is just another reassurance. Analyzing all aspects of the clean environment is very
 * difficult and developer should still take full responsibility for cleanup-ing all the changed resources.
 * This is turned off by default during CI builds and its main purpose is to be used during local debug. Can be switched
 * on/off by setting `cleanup.verifier` property
 */
@Slf4j
class CleanupVerifierExtension extends ContextAwareGlobalExtension {
    @Value('${cleanup.verifier}')
    boolean enabled

    @Autowired
    NorthboundService northbound

    @Override
    void delayedVisitSpec(SpecInfo spec) {
        if (!enabled) {
            return
        }
        spec.features.each {
            it.addInterceptor(new IMethodInterceptor() {
                @Override
                void intercept(IMethodInvocation invocation) throws Throwable {
                    invocation.proceed()
                    log.info("Running cleanup verifier for '$invocation.feature.name'")
                    assert northbound.getAllFlows().empty
                    northbound.getAllSwitches().each {
                        def validation = northbound.validateSwitch(it.switchId)
                        validation.verifyRuleSectionsAreEmpty()
                        validation.verifyMeterSectionsAreEmpty()
                    }
                    northbound.getAllLinks().each {
                        assert it.state == IslChangeType.DISCOVERED
                        assert it.cost == Constants.DEFAULT_COST || it.cost == 0
                        assert it.availableBandwidth == it.maxBandwidth
                    }
                }
            })
        }
    }
}
