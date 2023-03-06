package org.openkilda.functionaltests.listeners

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.model.IslStatus
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.SoftAssertions

import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value

/**
 * Performs certain checks after every spec/feature, tries to verify that environment is left clean.
 * This listener is meant to help to ensure that tests have 'good' cleanups. Note that is does not guarantee a
 * fully clean env and is just another reassurance. Analyzing all aspects of the clean environment is very
 * difficult and developer should still take full responsibility for cleanup-ing all the changed resources.
 * This is turned off by default during CI builds and its main purpose is to be used during local debug. Can be switched
 * on/off by setting `cleanup.verifier` property
 */
class CleanupVerifierListener extends AbstractSpringListener {
    @Value('${cleanup.verifier}')
    boolean enabled

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    TopologyDefinition topology
    @Autowired
    FloodlightsHelper flHelper
    @Autowired
    Database database
    @Value('${use.multitable}')
    boolean useMultitable

    @Override
    void afterIteration(IterationInfo iteration) {
        if (enabled && iteration.feature.spec.cleanupSpecMethods.empty) {
            runVerifications()
        }
    }

    @Override
    void afterSpec(SpecInfo spec) {
        if (enabled && !spec.cleanupSpecMethods.empty) {
            runVerifications()
        }
    }

    def runVerifications() {
        context.autowireCapableBeanFactory.autowireBean(this)
        assert northboundV2.getAllFlows().empty
        withPool {
            topology.activeSwitches.eachParallel { Switch sw ->
                def validation = northbound.validateSwitch(sw.dpId)
                validation.verifyRuleSectionsAreEmpty()
                validation.verifyMeterSectionsAreEmpty()
                def swProps = northbound.getSwitchProperties(sw.dpId)
                assert swProps.multiTable == useMultitable
                def s42Config = sw.prop
                if (s42Config) {
                    assert swProps.server42FlowRtt == s42Config.server42FlowRtt
                    assert swProps.server42Port == s42Config.server42Port
                    assert swProps.server42MacAddress == s42Config.server42MacAddress
                    assert swProps.server42Vlan == s42Config.server42Vlan
                    assert swProps.server42IslRtt == (s42Config.server42IslRtt == null ? "AUTO" : (s42Config.server42IslRtt ? "ENABLED" : "DISABLED"))
                }
            }
        }
        def regionVerifications = new SoftAssertions()
        flHelper.fls.forEach { fl ->
            def expectedSwitchIds = topology.activeSwitches.findAll { fl.region in it.regions }*.dpId
            if (!expectedSwitchIds.empty) {
                regionVerifications.checkSucceeds {
                    assert fl.floodlightService.switches*.switchId.sort() == expectedSwitchIds.sort()
                }
            }
        }
        regionVerifications.verify()
        withPool {
            database.getIsls(topology.isls).eachParallel {
                assert it.timeUnstable == null
                assert it.status == IslStatus.ACTIVE
                assert it.actualStatus == IslStatus.ACTIVE
                assert it.availableBandwidth == it.maxBandwidth
                assert it.availableBandwidth == it.speed
                assert it.cost == Constants.DEFAULT_COST || it.cost == 0
            }
        }
        assert northbound.getLinkProps(topology.isls).empty
    }
}
