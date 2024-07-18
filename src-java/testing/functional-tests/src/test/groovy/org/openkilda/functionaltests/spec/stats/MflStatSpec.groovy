package org.openkilda.functionaltests.spec.stats

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RO
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchConnectMode
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/fl-statistics")
@Narrative("""Now we have two FL instances: Management and Statistics.
- FL Stats: collect statistics only from the switches.
- FL Management: do the other work and can collect statistics as well when a switch doesn't connect to FL Stats.""")

class MflStatSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    FlowStats flowStats

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def setupSpec() {
        upTraffGenPortsIfRequired()
    }
    //TODO: split these long tests into set of the smaller ones after https://github.com/telstra/open-kilda/pull/5256
    // is merged into development
    @Tags([LOW_PRIORITY])
    def "System is able to collect stats from the statistic and management controllers"() {
        given: "A flow"
        assumeTrue(topology.activeTraffGens.size() > 1, "Require at least 2 switches with connected traffgen")
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch).withBandwidth(100).build()
                .createV1()

        def waitInterval = 10 //seconds

        when: "Generate traffic on the given flow"
        def startTime = new Date().getTime()
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward.tap{ udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stat in TSDB is created"
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId()).hasNonZeroValuesAfter(startTime)
        }

        when: "Leave src switch only with management controller and disconnect from stats"
        def statsBlockData = switchHelper.knockoutSwitchFromStatsController(srcSwitch)
        switchIsConnectedToFl(srcSwitch.dpId, true, false)
        def timeWhenSwitchWasDisconnectedFromFloodlight = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because management controller is set"
        def statFromMgmtController
        //first 60 seconds - trying to retrieve stats from management controller, next 60 seconds from stat controller
        Wrappers.wait(statsRouterRequestInterval * 2 + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchWasDisconnectedFromFloodlight)
        }

        when: "Leave src switch only with stats controller and disconnect from management"
        lockKeeper.reviveSwitch(srcSwitch, statsBlockData)
        switchIsConnectedToFl(srcSwitch.dpId, true, true)
        def mgmtBlockData = switchHelper.knockoutSwitch(srcSwitch, RW)
        switchIsConnectedToFl(srcSwitch.dpId, false, true)
        def timeWhenSwitchWasDisconnectedFromManagement = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because statistic controller is set"
        def statFromStatsController
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchWasDisconnectedFromManagement)
        }

        when: "Disconnect the src switch from both management and statistic controllers"
        statsBlockData = lockKeeper.knockoutSwitch(srcSwitch, RO)
        switchIsConnectedToFl(srcSwitch.dpId, false, false)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }
        def timeWhenSwitchWasDisconnectedFromBoth = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should not be collected because it is disconnected from controllers"
        def statAfterDeletingControllers
        Wrappers.timedLoop(statsRouterRequestInterval) {
            assert !flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchWasDisconnectedFromBoth)
            sleep((waitInterval * 1000).toLong())
        }

        when: "Restore default controllers on the src switches"
        lockKeeper.reviveSwitch(srcSwitch, statsBlockData)
        lockKeeper.reviveSwitch(srcSwitch, mgmtBlockData)
        switchIsConnectedToFl(srcSwitch.dpId, true, true)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.empty
        }

        then: "Old statistic should be collected"
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(startTime)
        }
    }

    //TODO: split these long tests into set of the smaller ones after https://github.com/telstra/open-kilda/pull/5256
    // is merged into development
    def "System is able to collect stats from the statistic and management controllers (v2)"() {
        given: "A flow"
        assumeTrue(topology.activeTraffGens.size() > 1, "Require at least 2 switches with connected traffgen")
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(100).build()
                .create()

        when: "Generate traffic on the given flow"
        def startTime = new Date().getTime()
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat in TSDB is created"
        def waitInterval = 10
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(startTime)
        }

        when: "Src switch is only left with management controller (no stats controller)"
        def statsBlockData = switchHelper.knockoutSwitchFromStatsController(srcSwitch)
        def needToRestoreConnectionToStats = true
        switchIsConnectedToFl(srcSwitch.dpId, true, false)
        def timeWhenSwitchWasDisconnectedFromFloodlight = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because management controller is still set"
        //first 60 seconds - trying to retrieve stats from management controller, next 60 seconds from stat controller
        Wrappers.wait(statsRouterRequestInterval * 2 + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchWasDisconnectedFromFloodlight)
        }

        when: "Set only statistic controller on the src switch and disconnect from management"
        lockKeeper.reviveSwitch(srcSwitch, statsBlockData)
        switchIsConnectedToFl(srcSwitch.dpId, true, true)
        needToRestoreConnectionToStats = false
        def mgmtBlockData = switchHelper.knockoutSwitch(srcSwitch, RW)
        def needToRestoreConnectionToManagement = true
        switchIsConnectedToFl(srcSwitch.dpId, false, true)
        def timeWhenSwitchWasDisconnectedFromManagement = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because statistic controller is set"
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchWasDisconnectedFromManagement)
        }

        when: "Disconnect the src switch from both management and statistic controllers"
        statsBlockData = lockKeeper.knockoutSwitch(srcSwitch, RO)
        needToRestoreConnectionToStats = true
        switchIsConnectedToFl(srcSwitch.dpId, false, false)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }
        def timeWhenSwitchWasDisconnectedFromBoth = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should not be collected because it is disconnected from controllers"
        Wrappers.timedLoop(statsRouterRequestInterval) {
            sleep((waitInterval * 1000).toLong())
            assert !flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchWasDisconnectedFromBoth)
        }

        when: "Restore default controllers on the src switch"
        lockKeeper.reviveSwitch(srcSwitch, statsBlockData)
        lockKeeper.reviveSwitch(srcSwitch, mgmtBlockData)
        switchIsConnectedToFl(srcSwitch.dpId, true, true)
        needToRestoreConnectionToStats = false
        needToRestoreConnectionToManagement = false
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.empty
            assert flow.retrieveFlowStatus().status != FlowState.DOWN
        }

        then: "Old statistic should be collected"
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(startTime)

        }
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "System is able to collect stats if at least 1 stats or management controller is available"() {
        given: "A flow, src switch is connected to 2 RW and 2 RO floodlights"
        assumeTrue(topology.activeTraffGens.size() > 1, "Require at least 2 switches with connected traffgen")
        def srcSwitch = topology.activeTraffGens*.switchConnected.find { flHelper.filterRegionsByMode(it.regions, RW).size() == 2 &&
            flHelper.filterRegionsByMode(it.regions, RO).size() == 2 }
        assumeTrue(srcSwitch != null, "This test requires a tg switch in 2 RW regions and 2 RO regions")
        def dstSwitch = topology.activeTraffGens*.switchConnected.find { it.dpId != srcSwitch.dpId }
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(100).build()
                .create()

        when: "Src switch is only left with 1 management controller (no stats controllers)"
        def regionToStay = findMgmtFls(northboundV2.getSwitchConnections(srcSwitch.dpId))*.regionName.first()
        def blockData = switchHelper.knockoutSwitch(srcSwitch, srcSwitch.regions - regionToStay)
        Wrappers.wait(WAIT_OFFSET / 2) { with (northboundV2.getSwitchConnections(srcSwitch.dpId).connections) {
            it*.regionName == [regionToStay]
            it*.connectMode == [SwitchConnectMode.READ_WRITE.toString()]
        } }

        and: "Generate traffic on the given flow"
        def startTime = new Date().getTime()
        def traffExam = traffExamProvider.get()
        Exam exam = flow.traffExam(traffExam, flow.maximumBandwidth, 5).forward.tap { udp = true }
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected (first RW switch available)"
        def waitInterval = 10
        //first 60 seconds - trying to retrieve stats from management controller, next 60 seconds from stat controller
        Wrappers.wait(statsRouterRequestInterval * 2 + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(startTime)
        }

        when: "Src switch is only left with the other management controller (no stats controllers)"
        lockKeeper.reviveSwitch(srcSwitch, blockData)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northboundV2.getSwitchConnections(srcSwitch.dpId).connections.size() == 4
        }
        // '.first' in the line below, just for getting String instead of Array.
        regionToStay = (findMgmtFls(northboundV2.getSwitchConnections(srcSwitch.dpId))*.regionName - regionToStay).first()
        blockData = switchHelper.knockoutSwitch(srcSwitch, srcSwitch.regions - regionToStay)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northboundV2.getSwitchConnections(srcSwitch.dpId).connections*.regionName == [regionToStay]
        }
        def timeWhenSwitchLeftWithoutStatsControllers = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected (second RW switch available)"
        //first 60 seconds - trying to retrieve stats from management controller, next 60 seconds from stat controller
        Wrappers.wait(statsRouterRequestInterval * 2 + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchLeftWithoutStatsControllers)
        }

        when: "Set only 1 statistic controller on the src switch and disconnect from management"
        lockKeeper.reviveSwitch(srcSwitch, blockData)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northboundV2.getSwitchConnections(srcSwitch.dpId).connections.size() == 4
        }
        regionToStay = findStatFls(northboundV2.getSwitchConnections(srcSwitch.dpId))*.regionName.first()
        blockData = switchHelper.knockoutSwitch(srcSwitch, srcSwitch.regions - regionToStay)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northboundV2.getSwitchConnections(srcSwitch.dpId).connections*.regionName == [regionToStay]
        }
        def timeWhenSwitchLeftWithoutManagementControllers = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected (first RO switch available)"
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchLeftWithoutManagementControllers)
        }

        when: "Set only other statistic controller on the src switch and disconnect from management"
        switchHelper.reviveSwitch(srcSwitch, blockData)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northboundV2.getSwitchConnections(srcSwitch.dpId).connections.size() == 4
        }
        regionToStay = (findStatFls(northboundV2.getSwitchConnections(srcSwitch.dpId))*.regionName - regionToStay).first()
        switchHelper.knockoutSwitch(srcSwitch, srcSwitch.regions - regionToStay)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northboundV2.getSwitchConnections(srcSwitch.dpId).connections*.regionName == [regionToStay]
        }
        def timeWhenSwitchLeftWithForeginStatsController = new Date().getTime()

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected (second RO switch available)"
        Wrappers.wait(statsRouterRequestInterval + WAIT_OFFSET, waitInterval) {
            flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, srcSwitch.getDpId())
                    .hasNonZeroValuesAfter(timeWhenSwitchLeftWithForeginStatsController)
        }
    }

    def switchIsConnectedToFl(SwitchId switchId, Boolean mgmt, Boolean stats) {
        Wrappers.wait(WAIT_OFFSET / 2) {
            def switchConnectionInfo = northboundV2.getSwitchConnections(switchId)
            assert !findMgmtFls(switchConnectionInfo).empty == mgmt
            assert !findStatFls(switchConnectionInfo).empty == stats
        }
    }

    def findStatFls(SwitchConnectionsResponse switchConnections) {
        return switchConnections.connections.findAll {
            it.connectMode == SwitchConnectMode.READ_ONLY.toString()
        }
    }

    def findMgmtFls(SwitchConnectionsResponse switchConnections) {
        return switchConnections.connections.findAll {
            it.connectMode == SwitchConnectMode.READ_WRITE.toString()
        }
    }
}
