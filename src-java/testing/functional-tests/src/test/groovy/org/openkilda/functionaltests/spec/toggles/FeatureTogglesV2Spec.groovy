package org.openkilda.functionaltests.spec.toggles

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowForbiddenToCreateExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToDeleteExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToUpdateExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.ResourceLock

import static org.openkilda.functionaltests.ResourceLockConstants.DEFAULT_FLOW_ENCAP
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

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
        def flowRequest = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        def flow = flowHelperV2.addFlow(flowRequest)

        when: "Set create_flow toggle to false"
        featureToggles.createFlowEnabled(false)

        and: "Try to create a new flow"
        flowHelperV2.addFlow(flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToCreateExpectedError(~/Flow create feature is disabled/).matches(e)

        and: "Update of previously existing flow is still possible"
        flowHelperV2.updateFlow(flow.flowId, flowRequest.tap { it.description = it.description + "updated" })

        and: "Delete of previously existing flow is still possible"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def "System forbids updating flows when 'update_flow' toggle is set to false"() {
        given: "Existing flow"
        def flowRequest = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelperV2.addFlow(flowRequest)

        when: "Set update_flow toggle to false"
        featureToggles.updateFlowEnabled(false)

        and: "Try to update the flow"
        northboundV2.updateFlow(flowRequest.flowId, flowRequest.tap { it.description = it.description + "updated" })

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToUpdateExpectedError(~/Flow update feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        flowHelperV2.addFlow(flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))
    }

    def "System forbids deleting flows when 'delete_flow' toggle is set to false"() {
        given: "Existing flow"
        def flowRequest = flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        def flow = flowHelperV2.addFlow(flowRequest)

        when: "Set delete_flow toggle to false"
        featureToggles.deleteFlowEnabled(false)

        and: "Try to delete the flow"
        northboundV2.deleteFlow(flowRequest.flowId)

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToDeleteExpectedError(~/Flow delete feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        def newFlow = flowHelperV2.addFlow(flowHelperV2.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1]))

        and: "Updating of flow is still possible"
        flowHelperV2.updateFlow(flowRequest.flowId, flowRequest.tap { it.description = it.description + "updated" })

        when: "Set delete_flow toggle back to true"
        featureToggles.deleteFlowEnabled(true)

        then: "Able to delete flows"
        flowHelper.deleteFlow(flow.flowId)
        flowHelper.deleteFlow(newFlow.flowId)
    }

    @Tags([HARDWARE, ISL_RECOVER_ON_FAIL])
    @ResourceLock(DEFAULT_FLOW_ENCAP)
    def "Flow encapsulation type is changed while auto rerouting according to 'flows_reroute_using_default_encap_type' \
feature toggle"() {
        given: "A switch pair which supports 'transit_vlan' and 'vxlan' encapsulation types"
        def swPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().withAtLeastNPaths(2).random()

        and: "The 'flows_reroute_using_default_encap_type' feature is enabled"
        featureToggles.flowsRerouteUsingDefaultEncapType(true)

        and: "A flow with default encapsulation"
        def initKildaConfig = kildaConfiguration.getKildaConfiguration()
        def flow = flowHelperV2.randomFlow(swPair).tap { encapsulationType = null }
        flowHelperV2.addFlow(flow)
        assert northboundV2.getFlow(flow.flowId).encapsulationType == initKildaConfig.flowEncapsulationType

        when: "Update default flow encapsulation type in kilda configuration"
        def newFlowEncapsulationType = initKildaConfig.flowEncapsulationType == "transit_vlan" ?
                FlowEncapsulationType.VXLAN : FlowEncapsulationType.TRANSIT_VLAN
        kildaConfiguration.updateFlowEncapsulationType(newFlowEncapsulationType)

        and: "Init a flow reroute by breaking current path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def islToBreak = pathHelper.getInvolvedIsls(currentPath).first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) != currentPath
        }

        and: "Encapsulation type is changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType == newFlowEncapsulationType.toString().toLowerCase()

        when: "Update default flow encapsulation type in kilda configuration"
        kildaConfiguration.updateFlowEncapsulationType(initKildaConfig.flowEncapsulationType)

        and: "Disable the 'flows_reroute_using_default_encap_type' feature toggle"
        featureToggles.flowsRerouteUsingDefaultEncapType(false)

        and: "Restore previous path"
        islHelper.restoreIsl(islToBreak)

        and: "Init a flow reroute by breaking a new current path"
        def newCurrentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def newIslToBreak = pathHelper.getInvolvedIsls(newCurrentPath).first()
        islHelper.breakIsl(newIslToBreak)

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) != newCurrentPath
        }

        and: "Encapsulation type is not changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != initKildaConfig.flowEncapsulationType
    }

    @Tags([ISL_RECOVER_ON_FAIL, LOW_PRIORITY])
    @ResourceLock(DEFAULT_FLOW_ENCAP)
    def "Flow encapsulation type is not changed while syncing/auto rerouting/updating according to \
'flows_reroute_using_default_encap_type' if switch doesn't support new type of encapsulation"() {
        given: "A switch pair which supports 'transit_vlan' and 'vxlan' encapsulation types"
        and: "The 'vxlan' encapsulation type is disabled in swProps on the src switch"
        def swPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().withAtLeastNPaths(2).random()
        def initSrcSwProps = switchHelper.getCachedSwProps(swPair.src.dpId)
        switchHelper.updateSwitchProperties(swPair.src, initSrcSwProps.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
        })

        and: "The 'flows_reroute_using_default_encap_type' feature is enabled"
        featureToggles.flowsRerouteUsingDefaultEncapType(true)

        and: "A flow with transit_vlan encapsulation"
        def flow = flowHelperV2.randomFlow(swPair).tap { encapsulationType = FlowEncapsulationType.TRANSIT_VLAN }
        flowHelperV2.addFlow(flow)

        when: "Set vxlan as default flow encapsulation type in kilda configuration if it is not set"
        def initGlobalConfig = kildaConfiguration.getKildaConfiguration()
        def vxlanEncapsulationType = FlowEncapsulationType.VXLAN
        (initGlobalConfig.flowEncapsulationType == vxlanEncapsulationType.toString().toLowerCase()) ?:
                kildaConfiguration.updateFlowEncapsulationType(vxlanEncapsulationType)

        and: "Init a flow reroute by breaking current path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def islToBreak = pathHelper.getInvolvedIsls(currentPath).first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is not rerouted"
        sleep(rerouteDelay * 1000)
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath

        and: "Encapsulation type is NOT changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is in DOWN state"
        wait(WAIT_OFFSET) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
        }

        when: "Update the flow"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.description = description + " updated" })
        wait(WAIT_OFFSET / 2) {
            assert northboundV2.getFlow(flow.flowId).description == flow.description
        }

        then: "Encapsulation type is NOT changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is rerouted and in UP state"
        wait(RULES_INSTALLATION_TIME) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        when: "Synchronize the flow"
        with(northbound.synchronizeFlow(flow.flowId)) { !it.rerouted }

        then: "Encapsulation type is NOT changed according to kilda configuration"
        northboundV2.getFlow(flow.flowId).encapsulationType != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is still in UP state"
        northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP

        cleanup:
        initGlobalConfig && initGlobalConfig.flowEncapsulationType != vxlanEncapsulationType.toString().toLowerCase() &&
                kildaConfiguration.updateKildaConfiguration(initGlobalConfig)
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "System doesn't reroute flow when flows_reroute_on_isl_discovery: false"() {
        given: "A flow with alternative paths"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        //you have to break all altPaths to avoid rerouting when flowPath is broken
        def flowPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def flowInvolvedIsls = pathHelper.getInvolvedIsls(flowPath)
        def altIsls = topology.getRelatedIsls(switchPair.src) - flowInvolvedIsls.first()
        islHelper.breakIsls(altIsls)

        and: "Set flowsRerouteOnIslDiscovery=false"
        featureToggles.flowsRerouteOnIslDiscoveryEnabled(false)

        when: "Break the flow path(bring port down on the src switch)"
        def islToBreak = flowInvolvedIsls.first()
        islHelper.breakIsl(islToBreak)

        then: "The flow becomes 'Down'"
        wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
            assert flowHelper.getHistoryEntriesByAction(flow.flowId, REROUTE_ACTION).find {
                it.taskId =~ (/.+ : retry #1 ignore_bw true/)
            }?.payload?.last()?.action == REROUTE_FAIL
            assert northboundV2.getFlowHistoryStatuses(flow.flowId, 1).historyStatuses*.statusBecome == ["DOWN"]
        }
        wait(WAIT_OFFSET) {
            def prevHistorySize = flowHelper.getHistorySize(flow.flowId)
            Wrappers.timedLoop(4) {
                //history size should no longer change for the flow, all retries should give up
                def newHistorySize = flowHelper.getHistorySize(flow.flowId)
                assert newHistorySize == prevHistorySize
                assert northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN
                sleep(500)
            }
        }
        when: "Restore all possible flow paths"
        islHelper.restoreIsls(altIsls + islToBreak)

        then: "The flow is still in 'Down' status, because flows_reroute_on_isl_discovery: false"
        assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN

        and: "Flow is not rerouted"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath
    }
}
