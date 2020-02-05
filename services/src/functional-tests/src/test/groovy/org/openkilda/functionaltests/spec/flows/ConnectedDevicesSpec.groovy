package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.model.Cookie.LLDP_INGRESS_COOKIE
import static org.openkilda.model.Cookie.LLDP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_COOKIE
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE
import static org.openkilda.model.Cookie.LLDP_TRANSIT_COOKIE
import static org.openkilda.model.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
import static org.openkilda.model.Cookie.VERIFICATION_UNICAST_RULE_COOKIE
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.model.Flow
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.ConnectedDeviceDto
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDeviceDto
import org.openkilda.testing.Constants
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.LldpData
import org.openkilda.testing.tools.ConnectedDevice

import groovy.transform.AutoClone
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Unroll

import javax.inject.Provider

@Slf4j
@Narrative("""
Verify ability to detect connected devices per flow endpoint (src/dst). 
Verify allocated Connected Devices resources and installed rules.""")
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/connected-devices-lldp")
class ConnectedDevicesSpec extends HealthCheckSpecification {

    @Value('${use.multitable}')
    boolean useMultiTable

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Unroll
    @Tags([Tag.TOPOLOGY_DEPENDENT])
    @IterationTags([
            @IterationTag(tags = [Tag.SMOKE], iterationNameRegex = /srcLldp=true and dstLldp=true/),
            @IterationTag(tags = [Tag.HARDWARE], iterationNameRegex = /VXLAN/)
    ])
    def "Able to create a #flowDescr flow with srcLldp=#data.srcEnabled and dstLldp=#data.dstEnabled"() {
        given: "A flow with enabled or disabled connected devices"
        assumeTrue("Ignored due to https://github.com/telstra/open-kilda/issues/2827",
                data.encapsulation != FlowEncapsulationType.VXLAN)
        def tgService = traffExamProvider.get()
        def flow = getFlowWithConnectedDevices(data)
        flow.encapsulationType = data.encapsulation.toString()

        and: "Switches with turned 'on' multiTable property"
        def initialSrcProps = enableMultiTableIfNeeded(data.srcEnabled, data.switchPair.src.dpId)
        def initialDstProps = enableMultiTableIfNeeded(data.dstEnabled, data.switchPair.dst.dpId)

        when: "Create a flow with connected devices"
        with(flowHelper.addFlow(flow)) {
            source.detectConnectedDevices.lldp == data.srcEnabled
            destination.detectConnectedDevices.lldp == data.dstEnabled
        }

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

        then: "Getting connecting devices shows corresponding devices on each endpoint if enabled"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            with(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == (data.srcEnabled ? 1 : 0)
                it.destination.lldp.size() == (data.dstEnabled ? 1 : 0)
                data.srcEnabled ? verifyEquals(it.source.lldp.first(), srcData) : true
                data.dstEnabled ? verifyEquals(it.destination.lldp.first(), dstData) : true
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Delete action removed all rules and meters"
        Wrappers.wait(WAIT_OFFSET) {
            validateSwitchHasNoFlowRulesAndMeters(flow.source.datapath)
            validateSwitchHasNoFlowRulesAndMeters(flow.destination.datapath)
        }

        cleanup: "Restore initial switch properties"
        restoreSwitchProperties(data.switchPair.src.dpId, initialSrcProps)
        restoreSwitchProperties(data.switchPair.dst.dpId, initialDstProps)
        [data.switchPair.src, data.switchPair.dst].each { database.removeConnectedDevices(it.dpId) }

        where:
        data <<
                [
                        new ConnectedDeviceTestData(protectedFlow: false, oneSwitch: false,
                                srcEnabled: false, dstEnabled: true,
                                encapsulation: FlowEncapsulationType.TRANSIT_VLAN),
                        new ConnectedDeviceTestData(protectedFlow: true, oneSwitch: false,
                                srcEnabled: true, dstEnabled: false,
                                encapsulation: FlowEncapsulationType.TRANSIT_VLAN),
                        new ConnectedDeviceTestData(protectedFlow: false, oneSwitch: false,
                                srcEnabled: false, dstEnabled: true,
                                encapsulation: FlowEncapsulationType.VXLAN),
                        new ConnectedDeviceTestData(protectedFlow: true, oneSwitch: true,
                                srcEnabled: true, dstEnabled: false,
                                encapsulation: FlowEncapsulationType.VXLAN)
                        //now assign each of the above data pcs a relevant switch pair
                ].each { it.switchPair = getUniqueSwitchPairs()[0] } +
                //now add more data, below iterations will be run for all unique switches with TG attached
                [
                        new ConnectedDeviceTestData(protectedFlow: false, oneSwitch: false, srcEnabled: true,
                                dstEnabled: true, encapsulation: FlowEncapsulationType.TRANSIT_VLAN),
                        new ConnectedDeviceTestData(protectedFlow: false, oneSwitch: true, srcEnabled: true,
                                dstEnabled: true, encapsulation: FlowEncapsulationType.TRANSIT_VLAN),
                        new ConnectedDeviceTestData(protectedFlow: true, oneSwitch: false, srcEnabled: true,
                                dstEnabled: true, encapsulation: FlowEncapsulationType.VXLAN)
                        //each of the above datapiece may repeat multiple times depending on amount of available TG switches
                ].collectMany { dataPiece ->
                    getUniqueSwitchPairs().collect {
                        def newDataPiece = dataPiece.clone()
                        newDataPiece.switchPair = it
                        newDataPiece
                    }
                }
        flowDescr = sprintf("%s%s%s", data.encapsulation, data.protectedFlow ? " protected" : "",
                data.oneSwitch ? " oneSwitch" : "")
    }

    @AutoClone
    private static class ConnectedDeviceTestData {
        boolean protectedFlow, oneSwitch, srcEnabled, dstEnabled
        FlowEncapsulationType encapsulation
        SwitchPair switchPair
    }

    @Unroll
    def "Able to update flow from srcLldpDevices=#oldSrcEnabled, dstLldpDevices=#oldDstEnabled to \
srcLldpDevices=#newSrcEnabled, dstLldpDevices=#newDstEnabled"() {
        given: "Switches with turned 'on' multiTable property"
        def flow = getFlowWithConnectedDevices(true, false, oldSrcEnabled, oldDstEnabled)
        def initialSrcProps = enableMultiTableIfNeeded(oldSrcEnabled || newSrcEnabled, flow.source.datapath)
        def initialDstProps = enableMultiTableIfNeeded(oldDstEnabled || newDstEnabled, flow.destination.datapath)

        and: "Created flow with enabled or disabled connected devices"
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

        cleanup: "Cleanup: delete the flow"
        flowHelper.deleteFlow(updatedFlow.flowId)

        and: "Restore initial switch properties"
        restoreSwitchProperties(flow.source.datapath, initialSrcProps)
        restoreSwitchProperties(flow.destination.datapath, initialDstProps)
        [flow.source.datapath, flow.destination.datapath].each { database.removeConnectedDevices(it) }

        where:
        [oldSrcEnabled, oldDstEnabled, newSrcEnabled, newDstEnabled] << [
                [false, false, false, false],
                [true, true, true, true],
                [true, true, false, false],
                [false, false, true, true],
                [false, true, true, false],
                [true, true, false, true]
        ]
    }

    /**
     * This is an edge case. Other tests for 'oneSwitch' only test single-switch single-port scenarios
     */
    def "Able to detect devices on a single-switch different-port flow"() {
        given: "A flow between different ports on the same switch"
        def sw = topology.activeTraffGens*.switchConnected.first()
        def initialProps = enableMultiTableIfNeeded(true, sw.dpId)

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

        cleanup: "Restore initial switch properties"
        restoreSwitchProperties(sw.dpId, initialProps)
        database.removeConnectedDevices(sw.dpId)
    }

    @Unroll
    def "Able to swap flow paths with connected devices (srcLldpDevices=#srcEnabled, dstLldpDevices=#dstEnabled)"() {
        given: "Switches with turned 'on' multiTable property"
        def flow = getFlowWithConnectedDevices(true, false, srcEnabled, dstEnabled)
        def initialSrcProps = enableMultiTableIfNeeded(srcEnabled, flow.source.datapath)
        def initialDstProps = enableMultiTableIfNeeded(dstEnabled, flow.destination.datapath)

        and: "Created protected flow with enabled or disabled connected devices"
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

        cleanup: "Cleanup: delete the flow"
        flowHelper.deleteFlow(swappedFlow.flowId)
        [flow.source.datapath, flow.destination.datapath].each { database.removeConnectedDevices(it) }

        and: "Restore initial switch properties"
        restoreSwitchProperties(flow.source.datapath, initialSrcProps)
        restoreSwitchProperties(flow.destination.datapath, initialDstProps)

        where:
        [srcEnabled, dstEnabled] << [
                [true, false],
                [true, true]
        ]
    }

    @Tidy
    def "Able to handle 'timeLastSeen' field when receive repeating packets from the same device"() {
        given: "Switches with turned 'on' multiTable property"
        def flow = getFlowWithConnectedDevices(false, false, true, false)
        def initialSrcProps = enableMultiTableIfNeeded(true, flow.source.datapath)

        and: "Created flow that detects connected devices"
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
        database.removeConnectedDevices(flow.source.datapath)

        and: "Restore initial switch properties"
        restoreSwitchProperties(flow.source.datapath, initialSrcProps)
    }

    @Tidy
    def "Able to detect different devices on the same port"() {
        given: "Switches with turned 'on' multiTable property"
        def flow = getFlowWithConnectedDevices(false, false, false, true)
        def initialDstProps = enableMultiTableIfNeeded(true, flow.destination.datapath)

        and: "Created flow that detects connected devices"
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
        database.removeConnectedDevices(flow.destination.datapath)

        and: "Restore initial switch properties"
        restoreSwitchProperties(flow.destination.datapath, initialDstProps)
    }

    @Tidy
    def "System properly detects devices if feature is 'off' on switch level and 'on' on flow level"() {
        given: "A switch with devices feature turned off"
        def sw = topology.activeTraffGens[0].switchConnected
        def initialProps = enableMultiTableIfNeeded(true, sw.dpId)
        assert !northbound.getSwitchProperties(sw.dpId).switchLldp

        when: "Device sends an lldp packet into a free switch port"
        def lldpData = LldpData.buildRandom()
        def deviceVlan = 666
        def tg = topology.getTraffGen(sw.dpId)
        def device = new ConnectedDevice(traffExamProvider.get(), tg, deviceVlan)
        device.sendLldp(lldpData)

        then: "No devices are detected for the switch"
        Wrappers.timedLoop(2) {
            assert northboundV2.getConnectedDevices(sw.dpId).ports.empty
            sleep(50)
        }

        when: "Flow is created on a target switch with devices feature 'on'"
        def dst = topology.activeSwitches.find { it.dpId != sw.dpId }
        def flow = flowHelper.randomFlow(sw, dst).tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesPayload(true, false)
            it.source.vlanId = deviceVlan
        }
        flowHelper.addFlow(flow)

        and: "Device sends an lldp packet into a flow port on that switch (with a correct flow vlan)"
        device.sendLldp(lldpData)

        then: "Device is registered as a flow device"
        Wrappers.wait(WAIT_OFFSET) {
            with(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == 1
                verifyEquals(it.source.lldp.first(), lldpData)
            }
        }

        and: "Device is registered per-switch"
        with(northboundV2.getConnectedDevices(sw.dpId).ports) {
            it.size() == 1
            it[0].portNumber == tg.switchPort
            it[0].lldp.first().vlan == flow.source.vlanId
            it[0].lldp.first().flowId == flow.id
            verifyEquals(it[0].lldp.first(), lldpData)
        }

        cleanup: "Remove created flow and device"
        flow && northbound.deleteFlow(flow.id)
        device && device.close()
        database.removeConnectedDevices(sw.dpId)

        and: "Restore initial switch properties"
        restoreSwitchProperties(sw.dpId, initialProps)
    }

    @Tidy
    def "System properly detects devices if feature is 'on' on switch level and 'off' on flow level"() {
        given: "A switch with devices feature turned on"
        def tg = topology.activeTraffGens[0]
        def sw = tg.switchConnected
        def initialProps = northbound.getSwitchProperties(sw.dpId)
        switchHelper.updateSwitchProperties(sw, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = true
            it.switchLldp = true
        })

        and: "Flow is created on a target switch with devices feature 'off'"
        def dst = topology.activeSwitches.find { it.dpId != sw.dpId }
        def flow = flowHelper.randomFlow(sw, dst).tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesPayload(false, false)
        }
        flowHelper.addFlow(flow)

        when: "Device sends an lldp packet into a flow port"
        def lldpData = LldpData.buildRandom()
        new ConnectedDevice(traffExamProvider.get(), topology.getTraffGen(sw.dpId), flow.source.vlanId).withCloseable {
            it.sendLldp(lldpData)
        }

        then: "Device is registered per-switch"
        Wrappers.wait(WAIT_OFFSET) {
            with(northboundV2.getConnectedDevices(sw.dpId).ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.first().vlan == flow.source.vlanId
                it[0].lldp.first().flowId == flow.id
                verifyEquals(it[0].lldp.first(), lldpData)
            }
        }

        then: "Device is registered as a flow device"
        with(northbound.getFlowConnectedDevices(flow.id)) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData)
        }

        cleanup: "Remove created flow and registered devices, revert switch props"
        northbound.deleteFlow(flow.id)
        database.removeConnectedDevices(sw.dpId)
        switchHelper.updateSwitchProperties(sw, initialProps)
    }

    def "Able to detect devices on free switch port (no flow or isl)"() {
        given: "A switch with devices feature turned on"
        def tg = topology.activeTraffGens[0]
        def sw = tg.switchConnected
        def initialProps = northbound.getSwitchProperties(sw.dpId)
        switchHelper.updateSwitchProperties(sw, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = true
            it.switchLldp = true
        })

        when: "Device sends an lldp packet into a free port"
        def lldpData = LldpData.buildRandom()
        def vlan = 123
        new ConnectedDevice(traffExamProvider.get(), tg, vlan).withCloseable {
            it.sendLldp(lldpData)
        }

        then: "Corresponding device is detected on a switch port"
        Wrappers.wait(WAIT_OFFSET) {
            with(northboundV2.getConnectedDevices(sw.dpId).ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.first().vlan == vlan
                verifyEquals(it[0].lldp.first(), lldpData)
            }
        }

        cleanup: "Turn off devices prop, remove connected devices"
        database.removeConnectedDevices(sw.dpId)
        switchHelper.updateSwitchProperties(sw, initialProps)
    }

    @Unroll
    def "Able to distinguish devices between default and non-default single-switch flows (#descr)"() {
        given: "A switch with devices feature turned on"
        def tg = topology.activeTraffGens[0]
        def sw = tg.switchConnected
        def initialProps = northbound.getSwitchProperties(sw.dpId)
        switchHelper.updateSwitchProperties(sw, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = true
            it.switchLldp = true
        })

        and: "A single-sw flow with lldp device feature 'on'"
        def flow = flowHelper.randomFlow(sw, sw).tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesPayload(true, false)
        }
        flowHelper.addFlow(flow)

        and: "A single-sw default flow with lldp device feature 'on'"
        def defaultFlow = flowHelper.randomFlow(sw, sw, true, [flow]).tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesPayload(true, false)
            it.source.portNumber = flow.source.portNumber
            srcDefault && (it.source.vlanId = 0)
            dstDefault && (it.destination.vlanId = 0)
        }
        flowHelper.addFlow(defaultFlow)

        when: "Device sends an lldp packet into a flow port with flow vlan"
        def lldpData = LldpData.buildRandom()
        new ConnectedDevice(traffExamProvider.get(), tg, flow.source.vlanId).withCloseable {
            it.sendLldp(lldpData)
        }

        then: "Corresponding device is detected on a switch port with reference to corresponding non-default flow"
        Wrappers.wait(WAIT_OFFSET) {
            with(northboundV2.getConnectedDevices(sw.dpId).ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.first().flowId == flow.id
                it[0].lldp.first().vlan == flow.source.vlanId
                verifyEquals(it[0].lldp.first(), lldpData)
            }
        }

        and: "Device is also visible as a flow device"
        with(northbound.getFlowConnectedDevices(flow.id)) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData)
        }

        and: "Device is NOT visible as a default flow device"
        northbound.getFlowConnectedDevices(defaultFlow.id).source.lldp.empty

        when: "Another device sends an lldp packet into a flow port with vlan different from flow vlan"
        def lldpData2 = LldpData.buildRandom()
        def vlan = flow.source.vlanId - 1
        new ConnectedDevice(traffExamProvider.get(), tg, vlan).withCloseable {
            it.sendLldp(lldpData2)
        }

        then: "Corresponding device is detected on a switch port with reference to corresponding default flow"
        Wrappers.wait(WAIT_OFFSET) {
            with(northboundV2.getConnectedDevices(sw.dpId).ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.size() == 2
                it[0].lldp.last().flowId == defaultFlow.id
                it[0].lldp.last().vlan == vlan
                verifyEquals(it[0].lldp.last(), lldpData2)
            }
        }

        and: "Device is not visible as a flow device"
        //it's a previous device, nothing changed
        with(northbound.getFlowConnectedDevices(flow.id)) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData)
        }

        and: "Device is visible as a default flow device"
        with(northbound.getFlowConnectedDevices(defaultFlow.id)) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData2)
        }

        cleanup: "Turn off devices prop, remove connected devices, remove flow"
        flowHelper.deleteFlow(flow.id)
        flowHelper.deleteFlow(defaultFlow.id)
        database.removeConnectedDevices(sw.dpId)
        switchHelper.updateSwitchProperties(sw, initialProps)

        where:
        srcDefault | dstDefault
        true       | false
        true       | true

        descr = "default ${getDescr(srcDefault, dstDefault)}"
    }

    @Unroll
    def "System properly detects device vlan in case of #descr"() {
        given: "Switches with turned 'on' multiTable property"
        def flow = getFlowWithConnectedDevices(true, false, true, true).tap {
            srcDefault && (it.source.vlanId = 0)
            dstDefault && (it.destination.vlanId = 0)
        }
        def srcTg = topology.activeTraffGens.find { it.switchConnected.dpId == flow.source.datapath }
        def dstTg = topology.activeTraffGens.find { it.switchConnected.dpId == flow.destination.datapath }
        def initialSrcProps = enableMultiTableIfNeeded(true, flow.source.datapath)
        def initialDstProps = enableMultiTableIfNeeded(true, flow.destination.datapath)

        and: "A flow with enbaled connected devices, #descr"
        flowHelper.addFlow(flow)

        when: "Two devices send lldp packet on each flow endpoint"
        def srcData = LldpData.buildRandom()
        def dstData = LldpData.buildRandom()
        def nonDefaultVlan = 777 //use this vlan if flow endpoint is 'default' and should catch any vlan
        def tgService = traffExamProvider.get()
        withPool {
            [[flow.source, srcData], [flow.destination, dstData]].eachParallel { endpoint, lldpData ->
                new ConnectedDevice(tgService, topology.getTraffGen(endpoint.datapath),
                        endpoint.vlanId ?: nonDefaultVlan).withCloseable {
                    it.sendLldp(lldpData)
                        }
            }
        }

        then: "Both device are registered for the flow on src and dst"
        Wrappers.wait(WAIT_OFFSET) {
            with(northbound.getFlowConnectedDevices(flow.id)) {
                it.source.lldp.size() == 1
                it.destination.lldp.size() == 1
                verifyEquals(it.source.lldp.first(), srcData)
                verifyEquals(it.destination.lldp.first(), dstData)
            }
        }

        and: "Device is registered on src switch"
        with(northboundV2.getConnectedDevices(flow.source.datapath).ports) {
            it.size() == 1
            it[0].portNumber == srcTg.switchPort
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow.id
            it[0].lldp.first().vlan == (flow.source.vlanId ?: nonDefaultVlan)
            verifyEquals(it[0].lldp.first(), srcData)
        }

        and: "Device is registered on dst switch"
        with(northboundV2.getConnectedDevices(flow.destination.datapath).ports) {
            it.size() == 1
            it[0].portNumber == dstTg.switchPort
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow.id
            it[0].lldp.first().vlan == (flow.destination.vlanId ?: nonDefaultVlan)
            verifyEquals(it[0].lldp.first(), dstData)
        }

        cleanup: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Restore initial switch properties"
        restoreSwitchProperties(flow.source.datapath, initialSrcProps)
        restoreSwitchProperties(flow.destination.datapath, initialDstProps)
        [flow.source.datapath, flow.destination.datapath].each { database.removeConnectedDevices(it) }

        where:
        srcDefault | dstDefault
        false      | true
        true       | true

        descr = "default(no-vlan) flow endpoints on ${getDescr(srcDefault, dstDefault)}"
    }

    def "System forbids to turn on 'connected devices per switch' on a single-table-mode switch"() {
        when: "Try to change switch props so that connected devices are 'on' but switch is in a single-table mode"
        def sw = topology.activeSwitches.first()
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).tap {
            it.multiTable = false
            it.switchLldp = true
        })

        then: "Bad request error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        e.responseBodyAsString.to(MessageError).errorMessage == "Illegal switch properties combination for switch " +
                "$sw.dpId. 'switchLldp' property can be set to 'true' only if 'multiTable' property is 'true'."
    }

    @Tidy
    def "System forbids to turn on 'connected devices per flow' on a single-table-mode switch"() {
        given: "Switch in single-table mode"
        def swPair = topologyHelper.switchPairs.first()
        def sw = swPair.src
        def initProps = northbound.getSwitchProperties(sw.dpId)
        SwitchHelper.updateSwitchProperties(sw, initProps.jacksonCopy().tap {
            it.multiTable = false
        })

        when: "Try to create an lldp-enabled flow using single-table switch as src"
        def flow = flowHelper.randomFlow(swPair).tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesPayload(true, false)
        }
        northbound.addFlow(flow)

        then: "Bad request error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        e.responseBodyAsString.to(MessageError).errorDescription == "Catching of LLDP packets supported only on " +
                "switches with enabled 'multiTable' switch feature. This feature is disabled on switch $sw.dpId."

        cleanup: "Restore switch props"
        flow && !e && flowHelper.deleteFlow(flow.id)
        SwitchHelper.updateSwitchProperties(sw, initProps)
    }

    @Ignore("not implemented yet")
    @Tidy
    def "System forbids to turn off multi-table mode if switch has an lldp-enabled flow"() {
        given: "Switch in multi-table mode"
        def swPair = topologyHelper.switchPairs.find { it.src.features.contains(SwitchFeature.MULTI_TABLE) }
        def sw = swPair.src
        def initProps = enableMultiTableIfNeeded(true, sw.dpId)

        when: "Create an lldp-enabled flow"
        def flow = flowHelper.randomFlow(swPair).tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesPayload(true, false)
        }
        flowHelper.addFlow(flow)

        and: "Try disabling multi-table mode on a switch where lldp-per-flow is being enabled"
        northbound.updateSwitchProperties(sw.dpId, initProps.jacksonCopy().tap { it.multiTable = false })

        then: "Error is returned, stating that this operation cannot be performed"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        e.responseBodyAsString.to(MessageError).errorDescription == "Catching of LLDP packets supported only on " +
                "switches with enabled 'multiTable' switch feature. This feature is disabled on switch $sw.dpId."

        cleanup: "Restore switch props"
        flow && !e && flowHelper.deleteFlow(flow.id)
        SwitchHelper.updateSwitchProperties(sw, initProps)
    }

    /**
     * Returns a potential flow for creation according to passed params.
     * Note that for 'oneSwitch' it will return a single-port single-switch flow. There is no ability to obtain
     * single-switch different-port flow via this method.
     */
    private FlowPayload getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled, SwitchPair switchPair) {
        assert !(oneSwitch && protectedFlow), "Cannot create one-switch flow with protected path"
        def flow = null
        if (oneSwitch) {
            flow = flowHelper.singleSwitchSinglePortFlow(switchPair.src)
        } else {
            flow = flowHelper.randomFlow(switchPair)
            flow.allocateProtectedPath = protectedFlow
        }
        flow.source.detectConnectedDevices = new DetectConnectedDevicesPayload(srcEnabled, false)
        flow.destination.detectConnectedDevices = new DetectConnectedDevicesPayload(dstEnabled, false)
        return flow
    }

    private FlowPayload getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled) {
        def tgSwPair = getUniqueSwitchPairs()[0]
        assert tgSwPair, "Unable to find a switchPair with traffgens for the requested flow arguments"
        getFlowWithConnectedDevices(protectedFlow, oneSwitch, srcEnabled, dstEnabled, tgSwPair)
    }


    private FlowPayload getFlowWithConnectedDevices(ConnectedDeviceTestData testData) {
        getFlowWithConnectedDevices(testData.protectedFlow, testData.oneSwitch, testData.srcEnabled,
                testData.dstEnabled, testData.switchPair)
    }

    private SwitchPropertiesDto enableMultiTableIfNeeded(boolean lldpEnabled, SwitchId switchId) {
        def initialProps = northbound.getSwitchProperties(switchId)
        if (lldpEnabled && !initialProps.multiTable) {
            def sw = topology.switches.find { it.dpId == switchId }
            switchHelper.updateSwitchProperties(sw, initialProps.jacksonCopy().tap {
                it.multiTable = true
            })
        }
        return initialProps
    }

    private void restoreSwitchProperties(SwitchId switchId, SwitchPropertiesDto initialProperties) {
        Switch sw = topology.switches.find { it.dpId == switchId }
        switchHelper.updateSwitchProperties(sw, initialProperties)
    }

    /**
     * Pick as little as possible amount of switch pairs to cover all unique switch models we have (only connected
     * to traffgens and lldp-enabled).
     */
    @Memoized
    List<SwitchPair> getUniqueSwitchPairs() {
        def tgSwitches = topology.activeTraffGens*.switchConnected
                                 .findAll { it.features.contains(SwitchFeature.MULTI_TABLE) }
        def unpickedTgSwitches = tgSwitches.unique(false) { [it.description, it.details.hardware].sort() }
        List<SwitchPair> switchPairs = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.findAll {
            it.src in tgSwitches && it.dst in tgSwitches
        }
        def result = []
        while (!unpickedTgSwitches.empty) {
            def pair = switchPairs.sort(false) { switchPair ->
                //prioritize swPairs with unique traffgens on both sides
                [switchPair.src, switchPair.dst].count { Switch sw ->
                    !unpickedTgSwitches.contains(sw)
                }
            }.first()
            //pick first pair and then re-sort considering updated list of unpicked switches
            result << pair
            unpickedTgSwitches = unpickedTgSwitches - pair.src - pair.dst
        }
        return result
    }

    private void validateFlowAndSwitches(Flow flow) {
        northbound.validateFlow(flow.flowId).each { assert it.asExpected }
        [flow.srcSwitch, flow.destSwitch].each {
            def validation = northbound.validateSwitch(it.switchId)
            switchHelper.verifyRuleSectionsAreEmpty(validation, ["missing", "excess"])
            if (it.ofVersion != "OF_12") {
                switchHelper.verifyMeterSectionsAreEmpty(validation, ["missing", "misconfigured", "excess"])
            }
        }
    }

    private void validateLldpMeters(Flow flow, boolean source) {
        def sw = source ? flow.srcSwitch : flow.destSwitch
        if (sw.ofVersion == "OF_12") {
            return //meters are not supported
        }

        def swProps = northbound.getSwitchProperties(sw.switchId)
        def postIngressMeterCount = swProps.multiTable ? 1 : 0
        def switchLldpMeterCount = swProps.switchLldp ? 1 : 0
        def vxlanMeterCount = sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN) ? 1 : 0

        def meters = northbound.getAllMeters(sw.switchId).meterEntries*.meterId

        assert meters.count {
            it == createMeterIdForDefaultRule(LLDP_POST_INGRESS_COOKIE).value
        } == postIngressMeterCount
        assert meters.count {
            it == createMeterIdForDefaultRule(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE).value
        } == postIngressMeterCount

        assert meters.count {
            it == createMeterIdForDefaultRule(LLDP_POST_INGRESS_VXLAN_COOKIE).value
        } == vxlanMeterCount

        assert meters.count {
            it == createMeterIdForDefaultRule(LLDP_INPUT_PRE_DROP_COOKIE).value
        } == switchLldpMeterCount
        assert meters.count { it == createMeterIdForDefaultRule(LLDP_TRANSIT_COOKIE).value } == switchLldpMeterCount
        assert meters.count { it == createMeterIdForDefaultRule(LLDP_INGRESS_COOKIE).value } == switchLldpMeterCount
    }

    private void validateLldpRulesOnSwitch(Flow flow, boolean source) {
        def sw = source ? flow.srcSwitch : flow.destSwitch
        def properties = northbound.getSwitchProperties(sw.switchId)

        def lldpEnabled = ((source ? flow.detectConnectedDevices.srcLldp : flow.detectConnectedDevices.dstLldp)
                || properties.switchLldp)

        def flowRelatedRuleCount = lldpEnabled ? 1 : 0
        def postIngressRuleCount = properties.multiTable ? 1 : 0
        def switchLldpRuleCount = properties.switchLldp ? 1 : 0
        def vxlanRuleCount = sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN) ? 1 : 0

        def allRules = northbound.getSwitchRules(sw.switchId).flowEntries

        assert allRules.findAll { Cookie.isLldpInputCustomer(it.cookie) }.size() == flowRelatedRuleCount

        assert allRules.findAll { it.cookie == LLDP_POST_INGRESS_COOKIE }.size() == postIngressRuleCount
        assert allRules.findAll { it.cookie == LLDP_POST_INGRESS_ONE_SWITCH_COOKIE }.size() == postIngressRuleCount

        assert allRules.findAll { it.cookie == LLDP_POST_INGRESS_VXLAN_COOKIE }.size() == vxlanRuleCount

        assert allRules.findAll { it.cookie == LLDP_INPUT_PRE_DROP_COOKIE }.size() == switchLldpRuleCount
        assert allRules.findAll { it.cookie == LLDP_INGRESS_COOKIE }.size() == switchLldpRuleCount
        assert allRules.findAll { it.cookie == LLDP_TRANSIT_COOKIE }.size() == switchLldpRuleCount
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
        //other non-mandatory lldp fields are out of scope for now. Most likely they are not properly parsed
        return true
    }

    def verifyEquals(SwitchConnectedDeviceDto device, LldpData lldp) {
        assert device.macAddress == lldp.macAddress
        assert device.chassisId == "Mac Addr: $lldp.chassisId" //for now TG sends it as hardcoded 'mac address' subtype
        assert device.portId == "Locally Assigned: $lldp.portNumber" //subtype also hardcoded for now on traffgen side
        assert device.ttl == lldp.timeToLive
        //other non-mandatory lldp fields are out of scope for now. Most likely they are not properly parsed
        return true
    }

    private static String getDescr(boolean src, boolean dst) {
        if (src && !dst) {
            "src only"
        } else if (!src && dst) {
            "dst only"
        } else if (src && dst) {
            "src and dst"
        } else {
            "none of the endpoints"
        }
    }
}
