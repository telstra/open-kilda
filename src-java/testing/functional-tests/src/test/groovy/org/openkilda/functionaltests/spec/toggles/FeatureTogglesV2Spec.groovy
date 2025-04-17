package org.openkilda.functionaltests.spec.toggles

import static org.openkilda.functionaltests.ResourceLockConstants.DEFAULT_FLOW_ENCAP
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.helpers.model.FlowActionType.*
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.functionaltests.helpers.model.FlowStatusHistoryEvent.*
import static org.openkilda.functionaltests.helpers.model.Isls.breakIsls
import static org.openkilda.functionaltests.helpers.model.Isls.restoreIsls
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowForbiddenToCreateExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToDeleteExpectedError
import org.openkilda.functionaltests.error.flow.FlowForbiddenToUpdateExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.ResourceLock
import spock.lang.Shared

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

    @Autowired
    @Shared
    FlowFactory flowFactory

    def "System forbids creating new flows when 'create_flow' toggle is set to false"() {
        given: "Existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)

        when: "Set create_flow toggle to false"
        featureToggles.createFlowEnabled(false)

        and: "Try to create a new flow"
        flowFactory.getBuilder(swPair).build().sendCreateRequest()

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToCreateExpectedError(~/Flow create feature is disabled/).matches(e)

        and: "Update of previously existing flow is still possible"
       flow.update(flow.tap { it.description = it.description + "updated" })

        and: "Delete of previously existing flow is still possible"
        flow.delete()
    }

    def "System forbids updating flows when 'update_flow' toggle is set to false"() {
        given: "Existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)

        when: "Set update_flow toggle to false"
        featureToggles.updateFlowEnabled(false)

        and: "Try to update the flow"
        flow.update(flow.tap { it.description = it.description + "updated" })

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToUpdateExpectedError(~/Flow update feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        flowFactory.getRandom(switchPairs.all().random())
    }

    def "System forbids deleting flows when 'delete_flow' toggle is set to false"() {
        given: "Existing flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)

        when: "Set delete_flow toggle to false"
        featureToggles.deleteFlowEnabled(false)

        and: "Try to delete the flow"
        flow.delete()

        then: "Error response is returned, explaining that feature toggle doesn't allow such operation"
        def e = thrown(HttpClientErrorException)
        new FlowForbiddenToDeleteExpectedError(~/Flow delete feature is disabled/).matches(e)

        and: "Creating new flow is still possible"
        def newFlow = flowFactory.getRandom(switchPairs.all().random())

        and: "Updating of flow is still possible"
        flow.update(flow.tap { it.description = it.description + "updated" })

        when: "Set delete_flow toggle back to true"
        featureToggles.deleteFlowEnabled(true)

        then: "Able to delete flows"
        flow.delete()
        newFlow.delete()
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
        def flow = flowFactory.getBuilder(swPair).withEncapsulationType(null).build().create()
        assert flow.retrieveDetails().encapsulationType.toString() == initKildaConfig.flowEncapsulationType

        when: "Update default flow encapsulation type in kilda configuration"
        def newFlowEncapsulationType = initKildaConfig.flowEncapsulationType == "transit_vlan" ?
                FlowEncapsulationType.VXLAN : FlowEncapsulationType.TRANSIT_VLAN
        kildaConfiguration.updateFlowEncapsulationType(newFlowEncapsulationType)

        and: "Init a flow reroute by breaking current path"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def islToBreak = flowPathInfo.getInvolvedIsls().first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert flow.retrieveAllEntityPaths().getPathNodes() != flowPathInfo.getPathNodes()
        }

        and: "Encapsulation type is changed according to kilda configuration"
        flow.retrieveDetails().encapsulationType.toString() == newFlowEncapsulationType.toString().toLowerCase()

        when: "Update default flow encapsulation type in kilda configuration"
        kildaConfiguration.updateFlowEncapsulationType(initKildaConfig.flowEncapsulationType)

        and: "Disable the 'flows_reroute_using_default_encap_type' feature toggle"
        featureToggles.flowsRerouteUsingDefaultEncapType(false)

        and: "Restore previous path"
        islHelper.restoreIsl(islToBreak)

        and: "Init a flow reroute by breaking a new current path"
        def newFlowPathInfo = flow.retrieveAllEntityPaths()
        def newIslToBreak = newFlowPathInfo.getInvolvedIsls().first()
        islHelper.breakIsl(newIslToBreak)

        then: "Flow is rerouted"
        wait(WAIT_OFFSET + rerouteDelay) {
            assert flow.retrieveAllEntityPaths().getPathNodes() != newFlowPathInfo.getPathNodes()
        }

        and: "Encapsulation type is not changed according to kilda configuration"
        flow.retrieveDetails().encapsulationType.toString() != initKildaConfig.flowEncapsulationType
    }

    @Tags([ISL_RECOVER_ON_FAIL, LOW_PRIORITY])
    @ResourceLock(DEFAULT_FLOW_ENCAP)
    def "Flow encapsulation type is not changed while syncing/auto rerouting/updating according to \
'flows_reroute_using_default_encap_type' if switch doesn't support new type of encapsulation"() {
        given: "A switch pair which supports 'transit_vlan' and 'vxlan' encapsulation types"
        and: "The 'vxlan' encapsulation type is disabled in swProps on the src switch"
        def swPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().withAtLeastNPaths(2).random()
        def initSrcSwProps = swPair.src.getCachedProps()
        swPair.src.updateProperties(initSrcSwProps.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
        })

        and: "The 'flows_reroute_using_default_encap_type' feature is enabled"
        featureToggles.flowsRerouteUsingDefaultEncapType(true)

        and: "A flow with transit_vlan encapsulation"
        def flow = flowFactory.getBuilder(swPair).withEncapsulationType(TRANSIT_VLAN).build().create()

        when: "Set vxlan as default flow encapsulation type in kilda configuration if it is not set"
        def initGlobalConfig = kildaConfiguration.getKildaConfiguration()
        def vxlanEncapsulationType = FlowEncapsulationType.VXLAN
        (initGlobalConfig.flowEncapsulationType == vxlanEncapsulationType.toString().toLowerCase()) ?:
                kildaConfiguration.updateFlowEncapsulationType(vxlanEncapsulationType)

        and: "Init a flow reroute by breaking current path"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def islToBreak = flowPathInfo.getInvolvedIsls().first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is not rerouted"
        sleep(rerouteDelay * 1000)
        flow.retrieveAllEntityPaths().getPathNodes() == flowPathInfo.getPathNodes()

        and: "Encapsulation type is NOT changed according to kilda configuration"
        flow.retrieveDetails().encapsulationType.toString() != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is in DOWN state"
        wait(WAIT_OFFSET) {
            flow.retrieveFlowStatus().status == FlowState.DOWN
        }

        when: "Update the flow"
        String newDescription = flow.description + "updated"
        flow = flow.update( flow.tap { it.description = newDescription })
        assert flow.retrieveDetails().description == newDescription

        then: "Encapsulation type is NOT changed according to kilda configuration"
        flow.encapsulationType.toString() != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is rerouted and in UP state"
        wait(RULES_INSTALLATION_TIME) {
            flow.retrieveFlowStatus().status == FlowState.UP
        }

        when: "Synchronize the flow"
        with(flow.sync()) { !it.rerouted }

        then: "Encapsulation type is NOT changed according to kilda configuration"
        flow.retrieveDetails().encapsulationType.toString() != vxlanEncapsulationType.toString().toLowerCase()

        and: "Flow is still in UP state"
        flow.retrieveFlowStatus().status == FlowState.UP

        cleanup:
        initGlobalConfig && initGlobalConfig.flowEncapsulationType != vxlanEncapsulationType.toString().toLowerCase() &&
                kildaConfiguration.updateKildaConfiguration(initGlobalConfig)
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "System doesn't reroute flow when flows_reroute_on_isl_discovery: false"() {
        given: "A flow with alternative paths"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getRandom(switchPair)

        //you have to break all altPaths to avoid rerouting when flowPath is broken
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def flowInvolvedIsls = isls.all().findInPath(flowPathInfo)
        def altIsls = isls.all().relatedTo(switchPair.src).excludeIsls(flowInvolvedIsls).getListOfIsls()
        breakIsls(altIsls)

        and: "Set flowsRerouteOnIslDiscovery=false"
        featureToggles.flowsRerouteOnIslDiscoveryEnabled(false)

        when: "Break the flow path(bring port down on the src switch)"
        def islToBreak = flowInvolvedIsls.first()
        islToBreak.breakIt()

        then: "The flow becomes 'Down'"
        wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert flow.retrieveFlowStatus().status == FlowState.DOWN
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).find {
                it.taskId =~ (/.+ : retry #1 ignore_bw true/)
            }?.payload?.last()?.action == REROUTE_FAILED.payloadLastAction
            assert flow.retrieveFlowHistoryStatus(1).statusBecome == [DOWN]
        }
        wait(WAIT_OFFSET) {
            def prevHistorySize = flow.retrieveFlowHistory().entries.size()
            Wrappers.timedLoop(4) {
                //history size should no longer change for the flow, all retries should give up
                def newHistorySize = flow.retrieveFlowHistory().entries.size()
                assert newHistorySize == prevHistorySize
                assert flow.retrieveFlowStatus().status == FlowState.DOWN
                sleep(500)
            }
        }
        when: "Restore all possible flow paths"
        restoreIsls(altIsls + islToBreak)

        then: "The flow is still in 'Down' status, because flows_reroute_on_isl_discovery: false"
        assert flow.retrieveFlowStatus().status == FlowState.DOWN

        and: "Flow is not rerouted"
         flow.retrieveAllEntityPaths().getPathNodes() == flowPathInfo.getPathNodes()
    }
}
