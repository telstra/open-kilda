package org.openkilda.functionaltests.spec.toggles

import org.openkilda.functionaltests.error.flow.FlowForbiddenToCreateExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToDeleteExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToUpdateExpectedError

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.DEFAULT_FLOW_ENCAP
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.ResourceLock

@Narrative("""
Feature Toggles is a special lever that allows to turn on/off certain Kilda features. For example, we can disable
creation of new flows via Northbound API. This spec verifies that Feature Toggle restrictions are applied correctly.
""")
/*Note that the 'flowReroute' toggle is tested under AutoRerouteV2Spec#"Flow goes to 'Down' status when an intermediate
switch is disconnected and there is no ability to reroute".
BFD toggle is tested in BfdSpec
server42_isl_rtt toggle is tested in Server42IslRttSpec
server42_flow_rtt toggle is tested in Server42FlowRttSpec
flow_latency_monitoring_reactions toggle is tested in FlowMonitoringSpec
*/
@Tags(SMOKE)
@Isolated
class FeatureTogglesV2Spec extends HealthCheckSpecification {
    def "System forbids creating new flows when 'create_flow' toggle is set to false"() {
        given: "Existing flow"
        def flow = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelperV2.addFlow(flow)

        when: "Set create_flow toggle to false"
        northbound.toggleFeature(FeatureTogglesDto.builder().createFlowEnabled(false).build())

        and: "Try to create a new flow"
        northboundV2.addFlow(flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToCreateExpectedError(~/Flow create feature is disabled/).matches(e)
        and: "Update of previously existing flow is still possible"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.description = it.description + "updated" })

        and: "Delete of previously existing flow is still possible"
        flowHelperV2.deleteFlow(flow.flowId)

        and: "Cleanup: set create_flow toggle back to true"
        northbound.toggleFeature(FeatureTogglesDto.builder().createFlowEnabled(true).build())
    }

    def "System forbids updating flows when 'update_flow' toggle is set to false"() {
        given: "Existing flow"
        def flow = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelperV2.addFlow(flow)

        when: "Set update_flow toggle to false"
        northbound.toggleFeature(FeatureTogglesDto.builder().updateFlowEnabled(false).build())

        and: "Try to update the flow"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.description = it.description + "updated" })

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToUpdateExpectedError(~/Flow update feature is disabled/).matches(e)
        and: "Creating new flow is still possible"
        def newFlow = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelperV2.addFlow(newFlow)

        and: "Deleting of flows is still possible"
        [newFlow, flow].each { flowHelperV2.deleteFlow(it.flowId) }

        and: "Cleanup: set update_flow toggle back to true"
        northbound.toggleFeature(FeatureTogglesDto.builder().updateFlowEnabled(true).build())
    }

    def "System forbids deleting flows when 'delete_flow' toggle is set to false"() {
        given: "Existing flow"
        def flow = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelperV2.addFlow(flow)

        when: "Set delete_flow toggle to false"
        northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(false).build())

        and: "Try to delete the flow"
        northboundV2.deleteFlow(flow.flowId)

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToDeleteExpectedError(~/Flow delete feature is disabled/).matches(e)
        and: "Creating new flow is still possible"
        def newFlow = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelperV2.addFlow(newFlow)

        and: "Updating of flow is still possible"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.description = it.description + "updated" })

        when: "Set delete_flow toggle back to true"
        northbound.toggleFeature(FeatureTogglesDto.builder().deleteFlowEnabled(true).build())

        then: "Able to delete flows"
        [flow, newFlow].each { flowHelperV2.deleteFlow(it.flowId) }
    }

    @Tidy
    @Tags(HARDWARE)
    @ResourceLock(DEFAULT_FLOW_ENCAP)
    def "Flow encapsulation type is changed while auto rerouting according to 'flows_reroute_using_default_encap_type' \
feature toggle"() {
        given: "A switch pair which supports 'transit_vlan' and 'vxlan' encapsulation types"
        def swPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { switchHelper.isVxlanEnabled(it.dpId) } && it.paths.size() >= 2
        }
        assumeTrue(swPair as boolean, "Unable to find required switches in topology")

        and: "The 'flows_reroute_using_default_encap_type' feature is enabled"
        def initFeatureToggle = northbound.getFeatureToggles()
        !initFeatureToggle.flowsRerouteUsingDefaultEncapType && northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteUsingDefaultEncapType(true).build())

        and: "A flow with default encapsulation"
        def initKildaConfig = northbound.getKildaConfiguration()
        def flow = flowHelperV2.randomFlow(swPair).tap { encapsulationType = null }
        flowHelperV2.addFlow(flow)
        assert northboundV2.getFlow(flow.flowId).encapsulationType == initKildaConfig.flowEncapsulationType

        when: "Update default flow encapsulation type in kilda configuration"
        def newFlowEncapsulationType = initKildaConfig.flowEncapsulationType == "transit_vlan" ?
                FlowEncapsulationType.VXLAN : FlowEncapsulationType.TRANSIT_VLAN
        northbound.updateKildaConfiguration(new KildaConfigurationDto(flowEncapsulationType: newFlowEncapsulationType))

        and: "Init a flow reroute by breaking current path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def islToBreak = pathHelper.getInvolvedIsls(currentPath).first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        wait(WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.FAILED
        }

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) != currentPath
        }

        and: "Encapsulation type is changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        when: "Update default flow encapsulation type in kilda configuration"
        northbound.updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: initKildaConfig.flowEncapsulationType))

        and: "Disable the 'flows_reroute_using_default_encap_type' feature toggle"
        northbound.toggleFeature(FeatureTogglesDto.builder().flowsRerouteUsingDefaultEncapType(false).build())

        and: "Restore previous path"
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        and: "Init a flow reroute by breaking a new current path"
        def newCurrentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def newIslToBreak = pathHelper.getInvolvedIsls(newCurrentPath).first()
        antiflap.portDown(newIslToBreak.srcSwitch.dpId, newIslToBreak.srcPort)
        wait(WAIT_OFFSET) {
            assert islUtils.getIslInfo(newIslToBreak).get().state == IslChangeType.FAILED
        }

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) != newCurrentPath
        }

        and: "Encapsulation type is not changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != initKildaConfig.flowEncapsulationType

        cleanup:
        initFeatureToggle && northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteUsingDefaultEncapType(initFeatureToggle.flowsRerouteUsingDefaultEncapType).build())
        flow && flowHelperV2.deleteFlow(flow.flowId)
        islToBreak && !newIslToBreak && antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        newIslToBreak && antiflap.portUp(newIslToBreak.srcSwitch.dpId, newIslToBreak.srcPort)
        wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, islToBreak).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, islToBreak.reversed).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, newIslToBreak).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, newIslToBreak.reversed).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Ignore("https://github.com/telstra/open-kilda/issues/2955")
    @Tags(HARDWARE)
    @ResourceLock(DEFAULT_FLOW_ENCAP)
    def "Flow encapsulation type is not changed while syncing/auto rerouting/updating according to \
'flows_reroute_using_default_encap_type' if switch doesn't support new type of encapsulation"() {
        given: "A switch pair which supports 'transit_vlan' and 'vxlan' encapsulation types"
        and: "The 'vxlan' encapsulation type is disable in swProps on the src switch"
        def swPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            [it.src, it.dst].every { switchHelper.isVxlanEnabled(it.dpId) } && it.paths.size() >= 2
        }
        assumeTrue(swPair as boolean, "Unable to find required switches in topology")
        def initSrcSwProps = switchHelper.getCachedSwProps(swPair.src.dpId)
        northbound.updateSwitchProperties(swPair.src.dpId, initSrcSwProps.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
        })

        and: "The 'flows_reroute_using_default_encap_type' feature is enabled"
        def initFeatureToggle = northbound.getFeatureToggles()
        !initFeatureToggle.flowsRerouteUsingDefaultEncapType && northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteUsingDefaultEncapType(true).build())

        and: "A flow with transit_vlan encapsulation"
        def flow = flowHelperV2.randomFlow(swPair).tap { encapsulationType = FlowEncapsulationType.TRANSIT_VLAN }
        flowHelperV2.addFlow(flow)

        when: "Set vxlan as default flow encapsulation type in kilda configuration if it is not set"
        def initGlobalConfig = northbound.getKildaConfiguration()
        def vxlanEncapsulationType = FlowEncapsulationType.VXLAN
        (initGlobalConfig.flowEncapsulationType == vxlanEncapsulationType.toString().toLowerCase()) ?:
                northbound.updateKildaConfiguration(
                        new KildaConfigurationDto(flowEncapsulationType: vxlanEncapsulationType)
                )

        and: "Init a flow reroute by breaking current path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def islToBreak = pathHelper.getInvolvedIsls(currentPath).first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        wait(WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.FAILED
        }

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) != currentPath
        }

        and: "Encapsulation type is NOT changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is in UP state"
        northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP

        when: "Update the flow"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.description = description + " updated" })
        wait(WAIT_OFFSET / 2) {
            assert northboundV2.getFlow(flow.flowId).description == flow.description
        }

        then: "Encapsulation type is NOT changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is in UP state"
        northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP

        when: "Synchronize the flow"
        with(northbound.synchronizeFlow(flow.flowId)) { !it.rerouted }

        then: "Encapsulation type is NOT changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is in UP state"
        northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        islToBreak && antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        initFeatureToggle && northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteUsingDefaultEncapType(initFeatureToggle.flowsRerouteUsingDefaultEncapType).build())
        initGlobalConfig && initGlobalConfig.flowEncapsulationType != vxlanEncapsulationType.toString().toLowerCase() &&
                northbound.updateKildaConfiguration(initGlobalConfig)
        initSrcSwProps && northbound.updateSwitchProperties(swPair.src.dpId, initSrcSwProps)
        wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, islToBreak).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, islToBreak.reversed).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System doesn't reroute flow when flows_reroute_on_isl_discovery: false"() {
        given: "A flow with alternative paths"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.paths.size() >= 2
        } ?: assumeTrue(false, "No suiting switches found")
        def allFlowPaths = switchPair.paths
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        //you have to break all altPaths to avoid rerouting when flowPath is broken
        def flowPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def altPaths = allFlowPaths.findAll { it != flowPath && it.first().portNo != flowPath.first().portNo }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        def altPortsAreDown = true

        and: "Set flowsRerouteOnIslDiscovery=false"
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteOnIslDiscoveryEnabled(false)
                .build())
        def featureToogleIsUpdated = true

        when: "Break the flow path(bring port down on the src switch)"
        def getInvolvedIsls = pathHelper.getInvolvedIsls(flowPath)
        def islToBreak = getInvolvedIsls.first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        def mainPortIsDown = true

        then: "The flow becomes 'Down'"
        wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
            assert northbound.getFlowHistory(flow.flowId).find {
                it.action == REROUTE_ACTION && it.taskId =~ (/.+ : retry #1 ignore_bw true/)
            }?.payload?.last()?.action == REROUTE_FAIL
            assert northboundV2.getFlowHistoryStatuses(flow.flowId, 1).historyStatuses*.statusBecome == ["DOWN"]
        }
        wait(WAIT_OFFSET) {
            def prevHistorySize = northbound.getFlowHistory(flow.flowId).size()
            Wrappers.timedLoop(4) {
                //history size should no longer change for the flow, all retries should give up
                def newHistorySize = northbound.getFlowHistory(flow.flowId).size()
                assert newHistorySize == prevHistorySize
                assert northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN
                sleep(500)
            }
        }
        when: "Restore all possible flow paths"
        withPool {
            broughtDownPorts.everyParallel { antiflap.portUp(it.switchId, it.portNo) }
        }
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        altPortsAreDown = false
        mainPortIsDown = false

        then: "The flow is still in 'Down' status, because flows_reroute_on_isl_discovery: false"
        assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN

        and: "Flow is not rerouted"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        cleanup: "Restore topology to the original state, remove the flow, reset toggles"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        altPortsAreDown && broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        mainPortIsDown && antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        featureToogleIsUpdated && northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteOnIslDiscoveryEnabled(true)
                .build())
        wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts(topology.isls)
    }
}
