package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tag
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.meter.MeterEntry
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.model.Flow
import org.openkilda.model.LldpResources
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.ConnectedDeviceDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.LldpData
import org.openkilda.testing.tools.ConnectedDevice

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Unroll

import javax.inject.Provider

@Slf4j
@Narrative("""
Verify ability to detect connected devices per flow endpoint (src/dst). 
Verify allocated Connected Devices resources and installed rules.""")
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/connected-devices-lldp")
class FlowConnectedDeviceSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Unroll
    @IterationTag(tags=[Tag.SMOKE], iterationNameRegex = /srcLldp=true and dstLldp=true/)
    def "Able to create flow with protected=#protectedFlow oneSwitch=#oneSwitch \
srcLldp=#srcEnabled and dstLldp=#dstEnabled"() {
        given: "A flow with enabled or disabled connected devices"
        def tgService = traffExamProvider.get()
        def flow = getFlowWithConnectedDevices(protectedFlow, oneSwitch, srcEnabled, dstEnabled)

        when: "Create a flow with connected devices"
        flowHelper.addFlow(flow)

        then: "Flow and src/dst switches are valid"
        def createdFlow = database.getFlow(flow.id)
        validateFlowAndSwitches(createdFlow)

        and: "LLDP meters must be installed"
        validateLldpMeters(createdFlow, true)
        validateLldpMeters(createdFlow, false)

        and: "Ingress and LLDP rules must be installed"
        validateLldpRulesOnSwitch(createdFlow, true)
        validateLldpRulesOnSwitch(createdFlow, false)

        when: "Two devices send lldp packet on each flow endpoint"
        def srcData = LldpData.buildRandom()
        def dstData = LldpData.buildRandom()
        withPool {
            [[flow.source, srcData], [flow.destination, dstData]].eachParallel { endpoint, lldpData ->
                new ConnectedDevice(tgService, topology.getTraffGen(endpoint.datapath), endpoint.vlanId).withCloseable {
                    it.sendLldp(lldpData)
                }
            }
        }

        then: "Getting connecting devices shows corresponding devices on each endpoint"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            with(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == (srcEnabled ? 1 : 0)
                it.destination.lldp.size() == (dstEnabled ? 1 : 0)
                srcEnabled ? verifyEquals(it.source.lldp.first(), srcData) : true
                dstEnabled ? verifyEquals(it.destination.lldp.first(), dstData) : true
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Delete action removed all rules and meters"
        validateSwitchHasNoFlowRulesAndMeters(flow.source.datapath)
        validateSwitchHasNoFlowRulesAndMeters(flow.destination.datapath)

        where:
        [protectedFlow, oneSwitch, srcEnabled, dstEnabled] << [
                [false, false, false, true],
                [false, false, true, true],
                [false, true, true, true],
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

        then: "Flow and src/dst switches are valid"
        def updatedFlow = database.getFlow(flow.id)
        validateFlowAndSwitches(updatedFlow)

        and: "LLDP meters must be installed"
        validateLldpMeters(updatedFlow, true)
        validateLldpMeters(updatedFlow, false)

        and: "Ingress and LLDP rules must be installed"
        validateLldpRulesOnSwitch(updatedFlow, true)
        validateLldpRulesOnSwitch(updatedFlow, false)

        when: "Two devices send lldp packet on each flow endpoint"
        def srcData = LldpData.buildRandom()
        def dstData = LldpData.buildRandom()
        def tgService = traffExamProvider.get()
        withPool {
            [[flow.source, srcData], [flow.destination, dstData]].eachParallel { endpoint, lldpData ->
                new ConnectedDevice(tgService, topology.getTraffGen(endpoint.datapath), endpoint.vlanId).withCloseable {
                    it.sendLldp(lldpData)
                }
            }
        }

        then: "Getting connecting devices shows corresponding devices on each endpoint according to updated status"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            with(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == (newSrcEnabled ? 1 : 0)
                it.destination.lldp.size() == (newDstEnabled ? 1 : 0)
                newSrcEnabled ? verifyEquals(it.source.lldp.first(), srcData) : true
                newDstEnabled ? verifyEquals(it.destination.lldp.first(), dstData) : true
            }
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(updatedFlow.flowId)

        where:
        [oldSrcEnabled, oldDstEnabled, newSrcEnabled, newDstEnabled] << [
                [false, false, false, false],
                [true, true, true, true],
                [false, false, false, true],
                [false, false, true, false],
                [false, false, true, true],
                [false, true, false, false],
                [false, true, true, false],
                [false, true, true, true],
                [true, true, false, false],
                [true, true, true, false]
        ]
    }

    /**
     * This is an edge case. Other tests for 'oneSwitch' only test single-switch single-port scenarios
     */
    def "Able to detect devices on a single-switch different-port flow"() {
        given: "A flow between different ports on the same switch"
        def sw = topology.activeTraffGens*.switchConnected.first()
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.source.detectConnectedDevices = new DetectConnectedDevicesPayload(true, true)
        flowHelper.addFlow(flow)

        when: "A device connects to src endpoint and sends lldp"
        def lldpData = LldpData.buildRandom()
        new ConnectedDevice(traffExamProvider.get(), topology.getTraffGen(sw.dpId), flow.source.vlanId).withCloseable {
            it.sendLldp(lldpData)
        }

        then: "Connected device is recognized and saved"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == 1
                it.destination.lldp.empty
                verifyEquals(it.source.lldp[0], lldpData)
            }
        }

        when: "Remove the flow"
        northbound.deleteFlow(flow.id)

        and: "Try to get connected devices for removed flow"
        northbound.getFlowConnectedDevices(flow.id)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
    }

    @Unroll
    def "Able to swap flow paths with connected devices (srcLldpDevices=#srcEnabled, dstLldpDevices=#dstEnabled)"() {
        given: "Created protected flow with enabled or disabled connected devices"
        def flow = getFlowWithConnectedDevices(true, false, srcEnabled, dstEnabled)
        flowHelper.addFlow(flow)

        when: "Swap flow paths"
        northbound.swapFlowPath(flow.id)

        then: "Flow and src/dst switches are valid"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }
        def swappedFlow = database.getFlow(flow.id)
        validateFlowAndSwitches(swappedFlow)

        and: "LLDP meters must be installed"
        validateLldpMeters(swappedFlow, true)
        validateLldpMeters(swappedFlow, false)

        and: "Ingress and LLDP rules must be installed"
        validateLldpRulesOnSwitch(swappedFlow, true)
        validateLldpRulesOnSwitch(swappedFlow, false)

        when: "Two devices send lldp packet on each flow endpoint"
        def srcData = LldpData.buildRandom()
        def dstData = LldpData.buildRandom()
        def tgService = traffExamProvider.get()
        withPool {
            [[flow.source, srcData], [flow.destination, dstData]].eachParallel { endpoint, lldpData ->
                new ConnectedDevice(tgService, topology.getTraffGen(endpoint.datapath), endpoint.vlanId).withCloseable {
                    it.sendLldp(lldpData)
                }
            }
        }

        then: "Getting connecting devices shows corresponding devices on each endpoint"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            with(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == (srcEnabled ? 1 : 0)
                it.destination.lldp.size() == (dstEnabled ? 1 : 0)
                srcEnabled ? verifyEquals(it.source.lldp.first(), srcData) : true
                dstEnabled ? verifyEquals(it.destination.lldp.first(), dstData) : true
            }
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(swappedFlow.flowId)

        where:
        [srcEnabled, dstEnabled] << [
                [true, false],
                [true, true]
        ]
    }

    def "Able to handle 'timeLastSeen' field when receive repeating packets from the same device"() {
        given: "Flow that detects connected devices"
        def flow = getFlowWithConnectedDevices(false, false, true, false)
        flowHelper.addFlow(flow)

        and: "A connected device"
        def device = new ConnectedDevice(traffExamProvider.get(), topology.getTraffGen(flow.source.datapath),
                flow.source.vlanId)

        when: "Device sends lldp packet"
        def lldpData = LldpData.buildRandom()
        device.sendLldp(lldpData)

        then: "Device is registered for the flow, with timeLastSeen and timeFirstSeen values"
        def devices1 = Wrappers.retry(3, 0.5) {
            def devices = northbound.getFlowConnectedDevices(flow.id).source.lldp
            assert devices.size() == 1
            assert devices[0].timeFirstSeen == devices[0].timeLastSeen
            devices
        } as List<ConnectedDeviceDto>

        when: "Same packet is sent again"
        device.sendLldp(lldpData)

        then: "timeLastSeen is updated, timeFirstSeen remains the same"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            def devices = northbound.getFlowConnectedDevices(flow.id).source.lldp
            assert devices.size() == 1
            assert devices[0].timeFirstSeen == devices1[0].timeFirstSeen
            assert devices[0].timeLastSeen > devices1[0].timeLastSeen //yes, groovy can compare it properly
        }

        cleanup: "Disconnect the device and remove the flow"
        flow && flowHelper.deleteFlow(flow.id)
        device && device.close()
    }

    def "Able to detect different devices on the same port"() {
        given: "Flow that detects connected devices"
        def flow = getFlowWithConnectedDevices(false, false, false, true)
        flowHelper.addFlow(flow)

        and: "A connected device"
        def device = new ConnectedDevice(traffExamProvider.get(), topology.getTraffGen(flow.destination.datapath),
                flow.destination.vlanId)

        when: "Two completely different lldp packets are sent"
        def lldpData1 = LldpData.buildRandom()
        def lldpData2 = LldpData.buildRandom()
        device.sendLldp(lldpData1)
        device.sendLldp(lldpData2)

        then: "2 devices are registered for the flow"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            assert northbound.getFlowConnectedDevices(flow.id).destination.lldp.size() == 2
        }

        when: "Same device (same mac address) sends lldp packet with updated port number"
        device.sendLldp(lldpData1.tap {
            it.portNumber = (lldpData1.portNumber.toInteger() + 1).toString()
        })

        then: "Device is recognized as new one and total of 3 devices are registered for the flow"
        def foundDevices = Wrappers.retry(3, 0.5) {
            def devices = northbound.getFlowConnectedDevices(flow.id).destination.lldp
            assert devices.size() == 3
            devices
        } as List<ConnectedDeviceDto>

        when: "Request devices list with 'since' param equal to last registered device"
        def lastDevice = foundDevices.max { it.timeLastSeen }
        def filteredDevices = northbound.getFlowConnectedDevices(flow.id, lastDevice.timeLastSeen).destination.lldp

        then: "Only 1 device is returned (the latest registered)"
        filteredDevices.size() == 1
        filteredDevices.first() == lastDevice

        cleanup: "Disconnect the device and remove the flow"
        flow && flowHelper.deleteFlow(flow.id)
        device && device.close()
    }

    /**
     * Returns a potential flow for creation according to passed params.
     * Note that for 'oneSwitch' it will return a single-port single-switch flow. There is no ability to obtain
     * single-switch different-port flow via this method.
     */
    private FlowPayload getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled) {
        assert !(oneSwitch && protectedFlow), "Cannot create one-switch flow with protected path"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = null
        if (oneSwitch) {
            flow = flowHelper.singleSwitchSinglePortFlow(srcSwitch)
        } else {
            flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
            flow.allocateProtectedPath = protectedFlow
        }
        flow.source.detectConnectedDevices = new DetectConnectedDevicesPayload(srcEnabled, false)
        flow.destination.detectConnectedDevices = new DetectConnectedDevicesPayload(dstEnabled, false)
        return flow
    }

    private void validateFlowAndSwitches(Flow flow) {
        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        [flow.srcSwitch.switchId, flow.destSwitch.switchId].each {
            def validation = northbound.validateSwitch(it)
            switchHelper.verifyRuleSectionsAreEmpty(validation, ["missing", "excess"])
            switchHelper.verifyMeterSectionsAreEmpty(validation, ["missing", "misconfigured", "excess"])
        }
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
            count *= 2
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
            count *= 2
        }
        return count
    }

    private static boolean mustHaveLldp(Flow flow, boolean source) {
        return (source && flow.detectConnectedDevices.srcLldp) || (!source && flow.detectConnectedDevices.dstLldp)
    }

    def verifyEquals(ConnectedDeviceDto device, LldpData lldp) {
        assert device.macAddress == lldp.macAddress
        assert device.chassisId == "Mac Addr: $lldp.chassisId" //for now TG sends it as hardcoded 'mac address' subtype
        assert device.portId == "Locally Assigned: $lldp.portNumber" //subtype also hardcoded for now on traffgen side
        assert device.ttl == lldp.timeToLive
        return true
    }
}
