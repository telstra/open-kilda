package org.openkilda.functionaltests.spec.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.meter.MeterEntry
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.model.Flow
import org.openkilda.model.LldpResources
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import spock.lang.Narrative
import spock.lang.Unroll

@Slf4j
@Narrative("Verify allocated Connected Devices resources and installed rules.")
class FlowConnectedDeviceSpec extends HealthCheckSpecification {

    @Unroll
    def "Able to create flow with protectedFlow=#protectedFlow oneSwitch=#oneSwitch \
detectSrcLldpConnectedDevices=#srcEnabled and detectDstLldpConnectedDevices=#dstEnabled"() {
        given: "A flow with enabled or disabled connected devices"
        def flow = getFlowWithConnectedDevices(protectedFlow, oneSwitch, srcEnabled, dstEnabled)

        when: "Create a flow with connected devices"
        flowHelper.addFlow(flow)

        then: "LLDP meters must be installed"
        def createdFlow = database.getFlow(flow.id)
        validateLldpMeters(createdFlow, true)
        validateLldpMeters(createdFlow, false)

        and: "Ingress and LLDP rules must be installed"
        validateLldpRulesOnSwitch(createdFlow, true)
        validateLldpRulesOnSwitch(createdFlow, false)

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Ensure delete action removed all rules and meters"
        validateSwitchHasNoFlowRulesAndMeters(flow.source.switchDpId)
        validateSwitchHasNoFlowRulesAndMeters(flow.destination.switchDpId)

        where:
        [protectedFlow, oneSwitch, srcEnabled, dstEnabled] << [
                [false, false, false, false],
                [false, false, false, true],
                [false, false, true, false],
                [false, false, true, true],
                [false, true, false, false],
                [false, true, false, true],
                [false, true, true, false],
                [false, true, true, true],
                [true, false, false, false],
                [true, false, false, true],
                [true, false, true, false],
                [true, false, true, true]
        ]
    }

    @Unroll
    def "Able to update flow from srcLldpDevices=#oldSrcEnabled, dstLldpDevices=#oldDstEnabled to \
srcLldpDevices=#newSrcEnabled, dstLldpDevices=#newDstEnabled"() {
        given: "Created flow with enabled or disabled connected devices"
        def flow = getFlowWithConnectedDevices(true, false, oldSrcEnabled, oldDstEnabled)
        flowHelper.addFlow(flow)

        when: "Update the flow with connected devices"
        flow.source.detectConnectedDevices = new DetectConnectedDevicesPayload(newSrcEnabled, false)
        flow.destination.detectConnectedDevices = new DetectConnectedDevicesPayload(newDstEnabled, false)
        flowHelper.updateFlow(flow.id, flow)

        then: "LLDP meters must be installed"
        def updatedFlow = database.getFlow(flow.id)
        validateLldpMeters(updatedFlow, true)
        validateLldpMeters(updatedFlow, false)

        and: "Ingress and LLDP rules must be installed"
        validateLldpRulesOnSwitch(updatedFlow, true)
        validateLldpRulesOnSwitch(updatedFlow, false)

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(updatedFlow.flowId)

        where:
        [oldSrcEnabled, oldDstEnabled, newSrcEnabled, newDstEnabled] << [
                [false, false, false, false],
                [false, false, false, true],
                [false, false, true, false],
                [false, false, true, true],
                [false, true, false, false],
                [false, true, false, true],
                [false, true, true, false],
                [false, true, true, true],
                [true, false, false, false],
                [true, false, false, true],
                [true, false, true, false],
                [true, false, true, true],
                [true, true, false, false],
                [true, true, false, true],
                [true, true, true, false],
                [true, true, true, true]
        ]
    }

    @Unroll
    def "Able to swap flow paths srcLldpDevices=#srcEnabled, dstLldpDevices=#dstEnabled"() {
        given: "Created protected flow with enabled or disabled connected devices"
        def flow = getFlowWithConnectedDevices(true, false, srcEnabled, dstEnabled)
        flowHelper.addFlow(flow)

        when: "Swap flow paths"
        northbound.swapFlowPath(flow.id)

        then: "Wait paths will be swapped"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "LLDP meters must be installed"
        def swappedFlow = database.getFlow(flow.id)
        validateLldpMeters(swappedFlow, true)
        validateLldpMeters(swappedFlow, false)

        and: "Ingress and LLDP rules must be installed"
        validateLldpRulesOnSwitch(swappedFlow, true)
        validateLldpRulesOnSwitch(swappedFlow, false)

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(swappedFlow.flowId)

        where:
        [srcEnabled, dstEnabled] << [
                [false, false],
                [false, true],
                [true, false],
                [true, true]
        ]
    }

    private FlowCreatePayload getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled) {
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        if (oneSwitch) {
            dstSwitch = srcSwitch
        }
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.source.detectConnectedDevices = new DetectConnectedDevicesPayload(srcEnabled, false)
        flow.destination.detectConnectedDevices = new DetectConnectedDevicesPayload(dstEnabled, false)
        flow.allocateProtectedPath = protectedFlow
        return flow
    }

    private void validateLldpMeters(Flow flow, boolean source) {
        def switchId = source ? flow.srcSwitch.switchId : flow.destSwitch.switchId
        def lldpEnabled = source ? flow.detectConnectedDevices.srcLldp : flow.detectConnectedDevices.dstLldp
        def path = source ? flow.forwardPath : flow.reversePath

        def nonDefaultMeters = northbound.getAllMeters(switchId).meterEntries.findAll {
            !MeterId.isMeterIdOfDefaultRule(it.meterId)
        }
        assert getExpectedNonDefaultMeterCount(flow, source) == nonDefaultMeters.size()

        validateLldpMeter(nonDefaultMeters, path.lldpResources, lldpEnabled)

        if (flow.allocateProtectedPath) {
            def protectedPath = source ? flow.protectedForwardPath : flow.protectedReversePath
            validateLldpMeter(nonDefaultMeters, protectedPath.lldpResources, lldpEnabled)
        }
    }

    private static void validateLldpMeter(List<MeterEntry> meters, LldpResources lldpResources, boolean lldpEnabled) {
        if (lldpEnabled) {
            assert meters.count { it.meterId == lldpResources.meterId.value } == 1
        } else {
            assert lldpResources == null
        }
    }

    private void validateLldpRulesOnSwitch(Flow flow, boolean source) {
        def switchId = source ? flow.srcSwitch.switchId : flow.destSwitch.switchId
        def lldpEnabled = source ? flow.detectConnectedDevices.srcLldp : flow.detectConnectedDevices.dstLldp
        def path = source ? flow.forwardPath : flow.reversePath

        def allRules = northbound.getSwitchRules(switchId).flowEntries
        assert getExpectedLldpRulesCount(flow, source) == allRules.count { it.tableId == 1 }

        validateRules(allRules, path.cookie, path.lldpResources, lldpEnabled, false)

        if (flow.allocateProtectedPath) {
            def protectedPath = source ? flow.protectedForwardPath : flow.protectedReversePath
            validateRules(allRules, protectedPath.cookie, protectedPath.lldpResources, lldpEnabled, true)
        }
    }

    private static void validateRules(List<FlowEntry> allRules, Cookie flowCookie, LldpResources lldpResources,
                                      boolean lldpEnabled, boolean protectedPath) {
        def ingressRules = allRules.findAll { it.cookie == flowCookie.value }
        if (protectedPath) {
            assert ingressRules.size() == 0
        } else {
            assert ingressRules.size() == 1
            assert ingressRules[0].instructions.goToTable == (lldpEnabled ? 1 : null)
            assert ingressRules[0].tableId == 0
        }

        def lldpRules = allRules.findAll { it.tableId == 1 }
        if (lldpEnabled) {
            assert lldpRules.count { it.cookie == lldpResources.cookie.value } == 1
        } else {
            assert lldpResources == null
        }
    }

    private void validateSwitchHasNoFlowRulesAndMeters(SwitchId switchId) {
        assert northbound.getSwitchRules(switchId).flowEntries.count { !Cookie.isDefaultRule(it.cookie) } == 0
        assert northbound.getAllMeters(switchId).meterEntries.count { !MeterId.isMeterIdOfDefaultRule(it.meterId) } == 0
    }

    private static int getExpectedNonDefaultMeterCount(Flow flow, boolean source) {
        int count = 0
        if (mustHaveLldp(flow, source)) {
            count += 1
        }
        if (flow.oneSwitchFlow && mustHaveLldp(flow, !source)) {
            count += 1
        }
        if (flow.allocateProtectedPath) {
            count *=2
        }
        if (flow.bandwidth > 0) {
            count += flow.oneSwitchFlow ? 2 : 1
        }
        return count
    }

    private static int getExpectedLldpRulesCount(Flow flow, boolean source) {
        int count = 0
        if (mustHaveLldp(flow, source)) {
            count += 1
        }
        if (flow.oneSwitchFlow && mustHaveLldp(flow, !source)) {
            count += 1
        }
        if (flow.allocateProtectedPath) {
            count *=2
        }
        return count
    }

    private static boolean mustHaveLldp(Flow flow, boolean source) {
        return (source && flow.detectConnectedDevices.srcLldp) || (!source && flow.detectConnectedDevices.dstLldp);
    }
}
