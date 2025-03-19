package org.openkilda.functionaltests.spec.flows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule
import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_INPUT_PRE_DROP_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.FlowBuilder
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.northbound.dto.v1.flows.ConnectedDeviceDto
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDeviceDto
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.ArpData
import org.openkilda.testing.service.traffexam.model.LldpData

import com.github.javafaker.Faker
import groovy.transform.AutoClone
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import jakarta.inject.Provider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import java.time.Instant

@Slf4j
@Narrative("""
Verify ability to detect connected devices per flow endpoint (src/dst).
Verify allocated Connected Devices resources and installed rules.""")
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/connected-devices-lldp")

class ConnectedDevicesSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Tags([TOPOLOGY_DEPENDENT])
    @IterationTags([
            @IterationTag(tags = [SMOKE, SMOKE_SWITCHES], iterationNameRegex = /srcLldp=true and dstLldp=true/),
            @IterationTag(tags = [HARDWARE], iterationNameRegex = /VXLAN/)
    ])
    def "Able to create a #flowDescr flow with lldp and arp enabled on #devicesDescr, encapsulation #data.encapsulation, swPair: #hw"() {
        assumeTrue(data.encapsulation != FlowEncapsulationType.VXLAN,
                "Devices+VXLAN problem https://github.com/telstra/open-kilda/issues/3199")
        assumeTrue(data.switchPair.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2,
                "Unable to find swPair with protected path")

        given: "A flow with enabled or disabled connected devices"
        def swPair = !data.oneSwitch ? data.switchPair : switchPairs.singleSwitch()
                .getSwitchPairs().find { it.src == data.switchPair.src }
        def expectedFlowEntity = getFlowWithConnectedDevices(data)
                .withEncapsulationType(data.encapsulation).build()

        when: "Create a flow with connected devices"
        def flow = expectedFlowEntity.create()

        then: "Flow has been created successfully and validated"
        flow.hasTheSameDetectedDevicesAs(expectedFlowEntity)
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Source and destination switches pass validation"
        synchronizeAndCollectFixedDiscrepancies(swPair.toList()).isEmpty()

        and: "ARP/LLDP rules/meters have been installed"
        def connectedDevicesInfo = flow.retrieveDetailsFromDB().detectConnectedDevices
        swPair.src.rulesManager.waitForDeviceRelatedInputRules(connectedDevicesInfo.srcArp, connectedDevicesInfo.srcLldp)
        swPair.dst.rulesManager.waitForDeviceRelatedInputRules(connectedDevicesInfo.dstArp, connectedDevicesInfo.dstLldp)

        swPair.toList().each { sw ->
            def validationResponse = sw.validate()
            assert validationResponse.meters.asExpected
            [LLDP_POST_INGRESS_COOKIE, LLDP_POST_INGRESS_ONE_SWITCH_COOKIE, ARP_POST_INGRESS_COOKIE, ARP_POST_INGRESS_ONE_SWITCH_COOKIE].each { cookie ->
                assert validationResponse.meters.proper.findAll { it.meterId == createMeterIdForDefaultRule(cookie).value }.size() == 1
            }
        }

        when: "Devices send lldp and arp packets on each flow endpoint"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService)
        def srcLldpData = sourceConnectedDevice.sendLldp()
        def dstLldpData = destinationConnectedDevice.sendLldp()
        def srcArpData = sourceConnectedDevice.sendArp()
        def dstArpData = destinationConnectedDevice.sendArp()

        then: "Getting connecting devices shows corresponding devices on each endpoint if enabled"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == (data.srcEnabled ? 1 : 0)
                it.destination.lldp.size() == (data.dstEnabled ? 1 : 0)
                it.source.arp.size() == (data.srcEnabled ? 1 : 0)
                it.destination.arp.size() == (data.dstEnabled ? 1 : 0)
                data.srcEnabled ? verifyEquals(it.source.lldp.first(), srcLldpData) : true
                data.dstEnabled ? verifyEquals(it.destination.lldp.first(), dstLldpData) : true
                data.srcEnabled ? verifyEquals(it.source.arp.first(), srcArpData) : true
                data.dstEnabled ? verifyEquals(it.destination.arp.first(), dstArpData) : true
            }
        }

        when: "Delete the flow"
        flow.delete()

        then: "Delete action removed all rules and meters"
        Wrappers.wait(WAIT_OFFSET) {
            swPair.toList().each { sw ->
                assert sw.rulesManager.getNotDefaultRules().isEmpty()
                assert sw.metersManager.getNotDefaultMeters().isEmpty()
            }
        }

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
                        //each of the above datapieces may repeat multiple times depending on amount of available TG switches
                ].collectMany { dataPiece ->
                    getUniqueSwitchPairs().findAll {
                        //for protected flow
                        it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2
                    }.collect {
                        def newDataPiece = dataPiece.clone()
                        newDataPiece.switchPair = it
                        newDataPiece
                    }
                }
        flowDescr = sprintf("%s%s%s", data.encapsulation, data.protectedFlow ? " protected" : "",
                data.oneSwitch ? " oneSwitch" : "")
        devicesDescr = getDescr(data.srcEnabled, data.dstEnabled)
        hw = data.oneSwitch ? "${data.switchPair.src.hwSwString()}"
                : "${data.switchPair.src.hwSwString()} - ${data.switchPair.dst.hwSwString()}"
    }

    @AutoClone
    private static class ConnectedDeviceTestData {
        boolean protectedFlow, oneSwitch, srcEnabled, dstEnabled
        FlowEncapsulationType encapsulation
        SwitchPair switchPair
    }

    def "Able to update flow from srcDevices=#oldSrcEnabled, dstDevices=#oldDstEnabled to \
srcDevices=#newSrcEnabled, dstDevices=#newDstEnabled"() {
        given: "Flow with specific configuration of connected devices"
        def swPair = getUniqueSwitchPairs()?.first()
        assumeTrue(swPair as boolean, "Unable to find a switchPair with traffgens for the requested flow arguments")
        def flow = getFlowWithConnectedDevices(true, false,
                oldSrcEnabled, oldDstEnabled, swPair).build().create()

        when: "Update the flow with connected devices"
        def expectedFlowEntity = flow.deepCopy().tap {
            it.source.detectConnectedDevices = new DetectConnectedDevicesV2(newSrcEnabled, newSrcEnabled)
            it.destination.detectConnectedDevices = new DetectConnectedDevicesV2(newDstEnabled, newDstEnabled)
        }
        def updatedFlow = flow.update(expectedFlowEntity)

        then: "Flow has been created successfully and validated"
        updatedFlow.hasTheSameDetectedDevicesAs(expectedFlowEntity)
        updatedFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "ARP/LLDP rules have been installed"
        def detectedDevicesInfo = flow.retrieveDetailsFromDB().detectConnectedDevices
        swPair.src.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.srcArp, detectedDevicesInfo.srcLldp)
        swPair.dst.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.dstArp, detectedDevicesInfo.dstLldp)

        and: "Source and destination switches pass validation (includes meters check)"
        synchronizeAndCollectFixedDiscrepancies(swPair.toList()).isEmpty()

        when: "Devices send lldp and arp packets on each flow endpoint"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService)
        def srcLldpData = sourceConnectedDevice.sendLldp()
        def dstLldpData = destinationConnectedDevice.sendLldp()
        def srcArpData = sourceConnectedDevice.sendArp()
        def dstArpData = destinationConnectedDevice.sendArp()

        then: "Getting connecting devices shows corresponding devices on each endpoint according to updated status"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == (newSrcEnabled ? 1 : 0)
                it.destination.lldp.size() == (newDstEnabled ? 1 : 0)
                it.source.arp.size() == (newSrcEnabled ? 1 : 0)
                it.destination.arp.size() == (newDstEnabled ? 1 : 0)
                newSrcEnabled ? verifyEquals(it.source.lldp.first(), srcLldpData) : true
                newDstEnabled ? verifyEquals(it.destination.lldp.first(), dstLldpData) : true
                newSrcEnabled ? verifyEquals(it.source.arp.first(), srcArpData) : true
                newDstEnabled ? verifyEquals(it.destination.arp.first(), dstArpData) : true
            }
        }

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
    @Tags([SMOKE_SWITCHES])
    def "Able to detect devices on a single-switch different-port flow"() {
        given: "A flow between different ports on the same switch"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def sw = switches.all().withTraffGens().getListOfSwitches().find { it.traffGenPorts.size() > 1}

        def expectedFlowEntity = flowFactory.getSingleSwBuilder(sw).withDetectedDevicesOnSrc(true, true).build()
        def flow = expectedFlowEntity.create()
        assert flow.source.portNumber != flow.destination.portNumber

        when: "Device connects to src endpoint and send lldp and arp packets"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def lldpData = sourceConnectedDevice.sendLldp()
        def arpData = sourceConnectedDevice.sendArp()

        then: "LLDP and ARP connected devices are recognized and saved"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == 1
                it.source.arp.size() == 1
                it.destination.lldp.empty
                it.destination.arp.empty
                verifyEquals(it.source.lldp[0], lldpData)
                verifyEquals(it.source.arp[0], arpData)
            }
        }

        when: "Remove the flow"
        flow.delete()

        and: "Try to get connected devices for removed flow"
        flow.retrieveConnectedDevices()

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotFoundExpectedError("Flow ${flow.flowId} not found",
                ~/Could not get connected devices for non existent flow/).matches(e)
    }

    def "Able to swap flow paths with connected devices (srcDevices=#srcEnabled, dstDevices=#dstEnabled)"() {
        given: "Protected flow with connected devices"
        def swPair = getUniqueSwitchPairs()?.first()
        assumeTrue(swPair as boolean, "Unable to find a switchPair with traffgens for the requested flow arguments")
        def expectedFlowEntity = getFlowWithConnectedDevices(true, false,
                srcEnabled, dstEnabled, swPair).build()

        and: "Created protected flow with enabled or disabled connected devices"
        def flow = expectedFlowEntity.create()

        when: "Swap flow paths"
        flow = flow.swapFlowPath().waitForBeingInState(UP)

        then: "Flow has been swapped successfully and validated"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "ARP/LLDP rules have been installed"
        def detectedDevicesInfo = flow.retrieveDetailsFromDB().detectConnectedDevices
        swPair.src.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.srcArp, detectedDevicesInfo.srcLldp)
        swPair.dst.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.dstArp, detectedDevicesInfo.dstLldp)

        and: "Source and destination switches pass validation (includes meters check)"
        synchronizeAndCollectFixedDiscrepancies(swPair.toList()).isEmpty()

        when: "Devices send lldp and arp packets on each flow endpoint"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService)
        def srcLldpData = sourceConnectedDevice.sendLldp()
        def dstLldpData = destinationConnectedDevice.sendLldp()
        def srcArpData = sourceConnectedDevice.sendArp()
        def dstArpData = destinationConnectedDevice.sendArp()

        then: "Getting connecting devices shows corresponding devices on each endpoint"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == (srcEnabled ? 1 : 0)
                it.destination.lldp.size() == (dstEnabled ? 1 : 0)
                it.source.arp.size() == (srcEnabled ? 1 : 0)
                it.destination.arp.size() == (dstEnabled ? 1 : 0)
                srcEnabled ? verifyEquals(it.source.lldp.first(), srcLldpData) : true
                dstEnabled ? verifyEquals(it.destination.lldp.first(), dstLldpData) : true
                srcEnabled ? verifyEquals(it.source.arp.first(), srcArpData) : true
                dstEnabled ? verifyEquals(it.destination.arp.first(), dstArpData) : true
            }
        }

        where:
        [srcEnabled, dstEnabled] << [
                [true, false],
                [true, true]
        ]
    }

    def "Able to handle 'timeLastSeen' field when receive repeating LLDP packets from the same device"() {
        given: "Flow with connected devices has been created successfully"
        def flow = getFlowWithConnectedDevices(false, false, true, false)
                .build().create()

        when: "Connected device sends lldp packet"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def lldpData = sourceConnectedDevice.sendLldp()

        then: "Device is registered for the flow, with timeLastSeen and timeFirstSeen values"
        def devicesDetails = Wrappers.retry(3, 0.5) {
            def devices = flow.retrieveConnectedDevices().source.lldp
            assert devices.size() == 1
            assert Instant.parse(devices[0].timeFirstSeen) == Instant.parse(devices[0].timeLastSeen)
            devices
        } as List<ConnectedDeviceDto>

        when: "Same packet is sent again"
        sourceConnectedDevice.sendLldp(lldpData)

        then: "timeLastSeen is updated, timeFirstSeen remains the same"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            def currentDevicesDetails = flow.retrieveConnectedDevices().source.lldp
            assert currentDevicesDetails.size() == 1
            assert Instant.parse(currentDevicesDetails[0].timeFirstSeen) == Instant.parse(devicesDetails[0].timeFirstSeen)
            assert Instant.parse(currentDevicesDetails[0].timeLastSeen) > Instant.parse(devicesDetails[0].timeLastSeen)
        }
    }

    def "Able to handle 'timeLastSeen' field when receive repeating ARP packets from the same device"() {
        given: "Flow with connected devices has been created successfully"
        def flow = getFlowWithConnectedDevices(false, false, true, false)
                .build().create()

        and: "A connected device"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)

        when: "Device sends ARP packet"
        def arpData = sourceConnectedDevice.sendArp()

        then: "Device is registered for the flow, with timeLastSeen and timeFirstSeen values"
        def devicesDetails = Wrappers.retry(3, 0.5) {
            def devices = flow.retrieveConnectedDevices().source.arp
            assert devices.size() == 1
            assert Instant.parse(devices[0].timeFirstSeen) == Instant.parse(devices[0].timeLastSeen)
            devices
        } as List<ConnectedDeviceDto>

        when: "Same packet is sent again"
        sourceConnectedDevice.sendArp(arpData)

        then: "timeLastSeen is updated, timeFirstSeen remains the same"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            def currentDevicesDetails = flow.retrieveConnectedDevices().source.arp
            assert currentDevicesDetails.size() == 1
            assert Instant.parse(currentDevicesDetails[0].timeFirstSeen) == Instant.parse(devicesDetails[0].timeFirstSeen)
            assert Instant.parse(currentDevicesDetails[0].timeLastSeen) > Instant.parse(devicesDetails[0].timeLastSeen)
        }
    }

    def "Able to detect different devices on the same port (LLDP)"() {
        given: "Flow with connected devices has been created successfully"
        def flow = getFlowWithConnectedDevices(false, false, false, true)
                .build().create()
        when: "Two completely different lldp packets are sent"
        def tgService = traffExamProvider.get()
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService)
        def lldpData = destinationConnectedDevice.sendLldp()
        destinationConnectedDevice.sendLldp()

        then: "2 devices are registered for the flow"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            assert flow.retrieveConnectedDevices().destination.lldp.size() == 2
        }

        when: "Same device (same mac address) sends lldp packet with updated port number"
        destinationConnectedDevice.sendLldp(lldpData.tap {
            it.portNumber = (lldpData.portNumber.toInteger() + 1).toString()
        })

        then: "Device is recognized as new one and total of 3 devices are registered for the flow"
        def foundDevices = Wrappers.retry(3, 0.5) {
            def devices = flow.retrieveConnectedDevices().destination.lldp
            assert devices.size() == 3
            devices
        } as List<ConnectedDeviceDto>

        when: "Request devices list with 'since' param equal to last registered device"
        def lastDevice = foundDevices.max { Instant.parse(it.timeLastSeen) }
        def filteredDevices = flow.retrieveConnectedDevices(lastDevice.timeLastSeen).destination.lldp

        then: "Only 1 device is returned (the latest registered)"
        filteredDevices.size() == 1
        filteredDevices.first() == lastDevice
    }

    def "Able to detect different devices on the same port (ARP)"() {
        given: "Flow with connected devices has been created successfully"
        def flow = getFlowWithConnectedDevices(false, false, false, true)
                .build().create()

        when: "Two completely different ARP packets are sent"
        def tgServer = traffExamProvider.get()
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgServer)
        def arpData = destinationConnectedDevice.sendArp()
        destinationConnectedDevice.sendArp()

        then: "2 arp devices are registered for the flow"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            assert flow.retrieveConnectedDevices().destination.arp.size() == 2
        }

        when: "Same device (same IP address) sends ARP packet with updated mac address"
        //note that updating ip adress will also be considered a 'new' device
        destinationConnectedDevice.sendArp(arpData.tap {
            it.srcMac = new Faker().internet().macAddress("20")
        })

        then: "Device is recognized as new one and total of 3 arp devices are registered for the flow"
        def foundDevices = Wrappers.retry(3, 0.5) {
            def devices = flow.retrieveConnectedDevices().destination.arp
            assert devices.size() == 3
            devices
        } as List<ConnectedDeviceDto>

        when: "Request devices list with 'since' param equal to last registered device"
        def lastDevice = foundDevices.max { Instant.parse(it.timeLastSeen) }
        def filteredDevices = flow.retrieveConnectedDevices(lastDevice.timeLastSeen).destination.arp

        then: "Only 1 device is returned (the latest registered)"
        filteredDevices.size() == 1
        filteredDevices.first() == lastDevice
    }

    def "System properly detects devices if feature is 'off' on switch level and 'on' on flow level"() {
        given: "A switch with devices feature turned off"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def sw = switches.all().withTraffGens().random()
        def tg = topology.getActiveTraffGen(sw.switchId)
        def swProps = sw.getProps()
        assert !swProps.switchLldp
        assert !swProps.switchArp

        when: "Devices send arp and lldp packets into a free switch port"
        def lldpData = LldpData.buildRandom()
        def arpData = ArpData.buildRandom()
        def deviceVlan = 666
        def device = sw.addConnectedDevice(traffExamProvider.get(), tg, [deviceVlan])
        device.sendLldp(lldpData)
        device.sendArp(arpData)

        then: "No devices are detected for the switch"
        Wrappers.timedLoop(2) {
            assert sw.getConnectedDevices().ports.empty
            sleep(50)
        }

        when: "Flow is created on a target switch with devices feature 'on'"
        def dst = switches.all().withTraffGens().getListOfSwitches().find { it != sw }

        def flow = flowFactory.getBuilder(sw, dst).withDetectedDevicesOnSrc(true, true)
                .withSourceVlan(deviceVlan).withSourcePort(tg.switchPort).build().create()

        and: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        flow.pingAndCollectDiscrepancies().isEmpty()

        and: "Device sends an lldp+arp packet into a flow port on that switch (with a correct flow vlan)"
        device.sendLldp(lldpData)
        device.sendArp(arpData)

        then: "LLDP and ARP devices are registered as flow devices"
        Wrappers.wait(WAIT_OFFSET * 2) {
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == 1
                verifyEquals(it.source.lldp.first(), lldpData)
                it.source.arp.size() == 1
                verifyEquals(it.source.arp.first(), arpData)
            }
        }

        and: "Devices are registered per-switch"
        verifyAll(sw.getConnectedDevices().ports) {
            it.size() == 1
            it[0].portNumber == tg.switchPort
            it[0].lldp.first().vlan == flow.source.vlanId
            it[0].lldp.first().flowId == flow.flowId
            verifyEquals(it[0].lldp.first(), lldpData)
            it[0].arp.first().vlan == flow.source.vlanId
            it[0].arp.first().flowId == flow.flowId
            verifyEquals(it[0].arp.first(), arpData)
        }
    }

    def "System properly detects devices if feature is 'on' on switch level and 'off' on flow level"() {
        given: "A switch with devices feature turned on"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")

        def sw = switches.all().withTraffGens().random()
        def tg = topology.getActiveTraffGen(sw.switchId)
        def initialProps = sw.getCachedProps()
        sw.updateProperties(initialProps.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        and: "Flow is created on a target switch with devices feature 'off'"
        def dst = switches.all().getListOfSwitches().find { it != sw }
        def flow = flowFactory.getBuilder(sw, dst).withDetectedDevicesOnSrc(false, false)
                .build().create()

        when: "Devices send lldp and arp packets into a flow port"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def lldpData = sourceConnectedDevice.sendLldp()
        def arpData = sourceConnectedDevice.sendArp()

        then: "ARP and LLDP devices are registered per-switch"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.getConnectedDevices().ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.first().vlan == flow.source.vlanId
                it[0].lldp.first().flowId == flow.flowId
                verifyEquals(it[0].lldp.first(), lldpData)
                it[0].arp.first().vlan == flow.source.vlanId
                it[0].arp.first().flowId == flow.flowId
                verifyEquals(it[0].arp.first(), arpData)
            }
        }

        then: "Devices are registered as flow devices"
        verifyAll(flow.retrieveConnectedDevices()) {
            it.source.lldp.size() == 1
            it.source.arp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData)
            verifyEquals(it.source.arp.first(), arpData)
        }
    }

    @Tags([SMOKE_SWITCHES])
    def "Able to detect devices on free switch port (no flow or isl)"() {
        given: "A switch with devices feature turned on"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def sw = switches.all().withTraffGens().first()
        def tg = topology.getActiveTraffGen(sw.switchId)
        def initialProps = sw.getCachedProps()
        sw.updateProperties(initialProps.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        when: "Devices send lldp and arp packets into a free port"
        def lldpData = LldpData.buildRandom()
        def arpData = ArpData.buildRandom()
        def vlan = 123
        sw.addConnectedDevice(traffExamProvider.get(), tg, [vlan]).withCloseable {
            it.sendLldp(lldpData)
            it.sendArp(arpData)
        }

        then: "Corresponding devices are detected on a switch port"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.getConnectedDevices().ports.find { it.portNumber == tg.switchPort }) { port ->
                port.portNumber == tg.switchPort
                port.lldp.first().vlan == vlan
                verifyEquals(port.lldp.first(), lldpData)
                port.arp.first().vlan == vlan
                verifyEquals(port.arp.first(), arpData)
            }
        }
    }

    def "Able to distinguish devices between default and non-default single-switch flows (#descr)"() {
        given: "A switch with devices feature turned on"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def sw = switches.all().withTraffGens().random()
        def tg = topology.getActiveTraffGen(sw.switchId)
        def initialProps = sw.getCachedProps()
        sw.updateProperties(initialProps.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        and: "A single-sw flow with devices feature 'on'"
        def expectedFlowEntity = flowFactory.getSingleSwBuilder(sw).withDetectedDevicesOnSrc(true, true)
        def flow = expectedFlowEntity.build().create()

        and: "A single-sw default flow with devices feature 'on'"
        def defaultFlow = flowFactory.getSingleSwBuilder(sw, true, flow.occupiedEndpoints())
                .withDetectedDevicesOnSrc(true, true)
                .withSourcePort(flow.source.portNumber).build()
                .tap {
                    srcDefault && (it.source.vlanId = 0)
                    dstDefault && (it.destination.vlanId = 0)
                    //when single-switch flow has full port on src and dst, ports should be different on these endpoints
                    // as llpd and arp send from tg on src, we can use non-tg port for dst
                    dstDefault && (it.destination.portNumber = (sw.getRandomPortNumber( false, [flow.source.portNumber])))
                }
                .create()

        when: "Devices send lldp and arp packets into a flow port with flow vlan"
        def lldpData = LldpData.buildRandom()
        def arpData = ArpData.buildRandom()
        sw.addConnectedDevice(traffExamProvider.get(), tg, [flow.source.vlanId]).withCloseable {
            it.sendLldp(lldpData)
            it.sendArp(arpData)
        }

        then: "Corresponding devices are detected on a switch port with reference to corresponding non-default flow"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.getConnectedDevices().ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.first().flowId == flow.flowId
                it[0].lldp.first().vlan == flow.source.vlanId
                verifyEquals(it[0].lldp.first(), lldpData)
                it[0].arp.first().flowId == flow.flowId
                it[0].arp.first().vlan == flow.source.vlanId
                verifyEquals(it[0].arp.first(), arpData)
            }
        }

        and: "Devices are also visible as flow devices"
        verifyAll(flow.retrieveConnectedDevices()) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData)
            it.source.arp.size() == 1
            verifyEquals(it.source.arp.first(), arpData)
        }

        and: "Devices are NOT visible as a default flow devices"
        def flowDevices = defaultFlow.retrieveConnectedDevices()
        flowDevices.source.lldp.empty
        flowDevices.source.arp.empty

        when: "Other devices send lldp and arp packets into a flow port with vlan different from flow vlan"
        def lldpData2 = LldpData.buildRandom()
        def arpData2 = ArpData.buildRandom()
        def vlan = flow.source.vlanId - 1
        sw.addConnectedDevice(traffExamProvider.get(), tg, [vlan]).withCloseable {
            it.sendLldp(lldpData2)
            it.sendArp(arpData2)
        }

        then: "Corresponding devices are detected on a switch port with reference to corresponding default flow"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.getConnectedDevices().ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.size() == 2
                it[0].lldp.last().flowId == defaultFlow.flowId
                it[0].lldp.last().vlan == vlan
                verifyEquals(it[0].lldp.last(), lldpData2)
                it[0].arp.size() == 2
                it[0].arp.last().flowId == defaultFlow.flowId
                it[0].arp.last().vlan == vlan
                verifyEquals(it[0].arp.last(), arpData2)
            }
        }

        and: "Devices are not visible as a flow devices"
        //it's a previous devices, nothing changed
        verifyAll(flow.retrieveConnectedDevices()) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData)
            it.source.arp.size() == 1
            verifyEquals(it.source.arp.first(), arpData)
        }

        and: "Devices are visible as a default flow devices"
        verifyAll(defaultFlow.retrieveConnectedDevices()) {
            it.source.lldp.size() == 1
            verifyEquals(it.source.lldp.first(), lldpData2)
            it.source.arp.size() == 1
            verifyEquals(it.source.arp.first(), arpData2)
        }


        when: "Other devices send lldp and arp packets into a flow port with double vlans different from flow vlan"
        def lldpData3 = LldpData.buildRandom()
        def arpData3 = ArpData.buildRandom()
        def vlans = [vlan - 1, vlan - 2]
        sw.addConnectedDevice(traffExamProvider.get(), tg, vlans).withCloseable {
            it.sendLldp(lldpData3)
            it.sendArp(arpData3)
        }

        then: "Corresponding devices are detected on a switch port with reference to corresponding default flow"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.getConnectedDevices().ports) {
                it.size() == 1
                it[0].portNumber == tg.switchPort
                it[0].lldp.size() == 3
                it[0].lldp.last().flowId == defaultFlow.flowId
                it[0].lldp.last().vlan == vlans[0] //due to issue 3475
                verifyEquals(it[0].lldp.last(), lldpData3)
                it[0].arp.size() == 3
                it[0].arp.last().flowId == defaultFlow.flowId
                it[0].arp.last().vlan == vlans[0] //due to issue 3475
                verifyEquals(it[0].arp.last(), arpData3)
            }
        }

        and: "Devices are not visible as a flow devices"
        //it's a previous devices, nothing changed
        verifyAll(flow.retrieveConnectedDevices()) {
            it.source.lldp.size() == 1
            it.source.arp.size() == 1
        }

        and: "Devices are visible as a default flow devices"
        verifyAll(defaultFlow.retrieveConnectedDevices()) {
            it.source.lldp.size() == 2
            verifyEquals(it.source.lldp.sort { it.timeFirstSeen }.last(), lldpData3)
            it.source.arp.size() == 2
            verifyEquals(it.source.arp.sort { it.timeFirstSeen }.last(), arpData3)
        }

        where:
        srcDefault | dstDefault
        true       | false
        true       | true

        descr = "default ${getDescr(srcDefault, dstDefault)}"
    }

    def "System properly detects device vlan in case of #descr"() {
        given: "Flow with connected devices"
        def swPair = getUniqueSwitchPairs()?.first()
        assumeTrue(swPair as boolean, "Unable to find a switchPair with traffgens for the requested flow arguments")

        def expectedFlowEntity = getFlowWithConnectedDevices(true, false,
                true, true, swPair).build().tap {
            srcDefault && (it.source.vlanId = 0)
            dstDefault && (it.destination.vlanId = 0)
        }
        def srcTg = topology.getTraffGen(expectedFlowEntity.source.switchId, expectedFlowEntity.source.portNumber)
        def dstTg = topology.getTraffGen(expectedFlowEntity.destination.switchId, expectedFlowEntity.source.portNumber)

        and: "A flow with enbaled connected devices, #descr"
        def flow = expectedFlowEntity.create()
        def detectedDevicesInfo = flow.retrieveDetailsFromDB().detectConnectedDevices
        swPair.src.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.srcArp, detectedDevicesInfo.srcLldp)
        swPair.dst.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.dstArp, detectedDevicesInfo.dstLldp)

        when: "Devices send lldp and arp packets on each flow endpoint"
        //use this vlan if flow endpoint is 'default' and should catch any vlan
        def nonDefaultVlan = 777
        def tgService = traffExamProvider.get()
        //for the default port(vlanId=0) on the src/dst all tagged traffic will be caught, except tagged traffic for existing flows
        def sourceConnectedDevice = flow.source.vlanId ? flow.sourceConnectedDeviceExam(tgService) :
                flow.sourceConnectedDeviceExam(tgService, [nonDefaultVlan])
        def destinationConnectedDevice = flow.destination.vlanId ? flow.destinationConnectedDeviceExam(tgService) :
                flow.destinationConnectedDeviceExam(tgService, [nonDefaultVlan])

        def srcLldpData = sourceConnectedDevice.sendLldp()
        def dstLldpData = destinationConnectedDevice.sendLldp()
        def srcArpData = sourceConnectedDevice.sendArp()
        def dstArpData = destinationConnectedDevice.sendArp()

        then: "Devices are registered for the flow on src and dst"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == 1
                it.destination.lldp.size() == 1
                verifyEquals(it.source.lldp.first(), srcLldpData)
                verifyEquals(it.destination.lldp.first(), dstLldpData)
                it.source.arp.size() == 1
                it.destination.arp.size() == 1
                verifyEquals(it.source.arp.first(), srcArpData)
                verifyEquals(it.destination.arp.first(), dstArpData)
            }
        }

        and: "Devices are registered on src switch"
        verifyAll(swPair.src.getConnectedDevices().ports) {
            it.size() == 1
            it[0].portNumber == srcTg.switchPort
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow.flowId
            it[0].lldp.first().vlan == (flow.source.vlanId ?: nonDefaultVlan)
            verifyEquals(it[0].lldp.first(), srcLldpData)
            it[0].arp.size() == 1
            it[0].arp.first().flowId == flow.flowId
            it[0].arp.first().vlan == (flow.source.vlanId ?: nonDefaultVlan)
            verifyEquals(it[0].arp.first(), srcArpData)
        }

        and: "Device are registered on dst switch"
        verifyAll(swPair.dst.getConnectedDevices().ports) {
            it.size() == 1
            it[0].portNumber == dstTg.switchPort
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow.flowId
            it[0].lldp.first().vlan == (flow.destination.vlanId ?: nonDefaultVlan)
            verifyEquals(it[0].lldp.first(), dstLldpData)
            it[0].arp.size() == 1
            it[0].arp.first().flowId == flow.flowId
            it[0].arp.first().vlan == (flow.destination.vlanId ?: nonDefaultVlan)
            verifyEquals(it[0].arp.first(), dstArpData)
        }

        where:
        srcDefault | dstDefault
        false      | true
        true       | true

        descr = "default(no-vlan) flow endpoints on ${getDescr(srcDefault, dstDefault)}"
    }

    @Tags([TOPOLOGY_DEPENDENT])
    @IterationTag(tags = [HARDWARE], iterationNameRegex = /VXLAN/)
    def "System detects devices for a qinq(iVlan=#vlanId oVlan=#innerVlanId) flow with lldp and arp enabled on the src switch"() {
        assumeTrue(encapsulationType != FlowEncapsulationType.VXLAN,
                "Devices+VXLAN problem https://github.com/telstra/open-kilda/issues/3199")

        given: "Two switches connected to traffgen"
        def swPairs = switchPairs.all().neighbouring().withTraffgensOnBothEnds()
        // there is an issue with physical switch (lldp traffic)
        profile == "virtual" ?: swPairs.excludeSwitches([switches.all().getListOfSwitches().find { it.switchId.toString().contains("40")}])
        def switchPair = switchPairs.random()

        and: "A QinQ flow with enabled connected devices on src has been created"
        def flow = flowFactory.getBuilder(switchPair)
                .withSourceVlan(vlanId)
                .withSourceInnerVlan(innerVlanId)
                .withEncapsulationType(encapsulationType)
                .withDetectedDevicesOnSrc(true, true).build()
                .create()

        expect: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "ARP/LLDP rules have been installed"
        def detectedDevicesInfo = flow.retrieveDetailsFromDB().detectConnectedDevices
        switchPair.src.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.srcArp, detectedDevicesInfo.srcLldp)
        switchPair.dst.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.dstArp, detectedDevicesInfo.dstLldp)

        and: "Source and destination switches pass validation (includes meters check)"
        synchronizeAndCollectFixedDiscrepancies(switchPair.toList()).isEmpty()

        when: "Devices send lldp and arp packets on each flow endpoint"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService)

        def srcLldpData = sourceConnectedDevice.sendLldp()
        def srcArpData = sourceConnectedDevice.sendArp()
        destinationConnectedDevice.sendLldp()
        destinationConnectedDevice.sendArp()


        then: "Getting connecting devices shows corresponding devices on src endpoint"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == 1
                it.source.arp.size() == 1
                it.destination.lldp.empty
                it.destination.arp.empty
                verifyEquals(it.source.lldp.first(), srcLldpData)
                verifyEquals(it.source.arp.first(), srcArpData)
            }
        }

        and: "Devices are registered on the src switch only"
        verifyAll(switchPair.src.getConnectedDevices().ports) {
            it.size() == 1
            it[0].portNumber == flow.source.portNumber
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow.flowId
            it[0].lldp.first().vlan == (flow.source.vlanId ? flow.source.vlanId : innerVlanId) //due to issue 3475
            verifyEquals(it[0].lldp.first(), srcLldpData)
            it[0].arp.size() == 1
            it[0].arp.first().flowId == flow.flowId
            it[0].arp.first().vlan == (flow.source.vlanId ? flow.source.vlanId : innerVlanId) //due to issue 3475
            verifyEquals(it[0].arp.first(), srcArpData)
        }
        switchPair.dst.getConnectedDevices().ports.empty

        and: "Delete the flow"
        flow.delete()

        and: "Delete action removed all rules and meters"
        Wrappers.wait(WAIT_OFFSET) {
           switchPair.src.rulesManager.getNotDefaultRules().isEmpty()
           switchPair.src.metersManager.getNotDefaultMeters().isEmpty()
        }

        where:
        vlanId | innerVlanId | encapsulationType
        0      | 200         | FlowEncapsulationType.TRANSIT_VLAN
        100    | 200         | FlowEncapsulationType.TRANSIT_VLAN
        0      | 200         | FlowEncapsulationType.VXLAN
        100    | 200         | FlowEncapsulationType.VXLAN
    }

    def "System doesn't detect devices only if vlan match with outerVlan of qinq flow"() {
        given: "Two switches connected to traffgen"
        def switchPair = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()

        and: "A QinQ flow with enabled connected devices"
        def outerVlan = 100
        def flow = flowFactory.getBuilder(switchPair)
                .withSourceVlan(outerVlan)
                .withSourceInnerVlan(200)
                .withDetectedDevicesOnSrc(true, true).build()
                .create()

        def detectedDevicesInfo = flow.retrieveDetailsFromDB().detectConnectedDevices
        switchPair.src.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.srcArp, detectedDevicesInfo.srcLldp)
        switchPair.dst.rulesManager.waitForDeviceRelatedInputRules(detectedDevicesInfo.dstArp, detectedDevicesInfo.dstLldp)

        when: "Devices send lldp and arp packets on src flow endpoint and match outerVlan only"
        def tgService = traffExamProvider.get()
        def incorrectSourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService, [outerVlan])
        def incorrectDestinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService, [outerVlan])
        incorrectSourceConnectedDevice.sendLldp()
        incorrectDestinationConnectedDevice.sendLldp()
        incorrectSourceConnectedDevice.sendArp()
        incorrectDestinationConnectedDevice.sendArp()

        then: "Getting connecting devices doesn't show corresponding devices on src endpoint"
        Wrappers.timedLoop(3) {
            //under usual condition system needs some time for devices to appear, that's why timeLoop is used here
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.empty
                it.source.arp.empty
                it.destination.lldp.empty
                it.destination.arp.empty
            }
        }

        and: "Devices are not registered on the src/dst switches"
        switchPair.src.getConnectedDevices().ports.empty
        switchPair.dst.getConnectedDevices().ports.empty
    }

    @Tags([SMOKE_SWITCHES])
    def "Able to detect devices on a qinq single-switch different-port flow"() {
        given: "A flow between different ports on the same switch"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def sw = switches.all().withTraffGens().first()

        def flow = flowFactory.getSingleSwBuilder(sw,true)
                .withDetectedDevicesOnSrc(true, true)
                .withSourceVlan(vlanId)
                .withSourceInnerVlan(innerVlanId)
                .withEncapsulationType(encapsulationType).build()
                .create()

        when: "Device connects to src endpoint and send lldp and arp packets"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService)
        def lldpData = sourceConnectedDevice.sendLldp()
        def arpData = sourceConnectedDevice.sendArp()

        then: "LLDP and ARP connected devices are recognized and saved"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow.retrieveConnectedDevices()) {
                it.source.lldp.size() == 1
                it.source.arp.size() == 1
                it.destination.lldp.empty
                it.destination.arp.empty
                verifyEquals(it.source.lldp[0], lldpData)
                verifyEquals(it.source.arp[0], arpData)
            }
        }

        and: "Devices are registered on the switch"
        verifyAll(sw.getConnectedDevices().ports) {
            it.size() == 1
            it[0].portNumber == flow.source.portNumber
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow.flowId
            it[0].lldp.first().vlan == (flow.source.vlanId ? flow.source.vlanId : innerVlanId) //due to issue 3475
            verifyEquals(it[0].lldp.first(), lldpData)
            it[0].arp.size() == 1
            it[0].arp.first().flowId == flow.flowId
            it[0].arp.first().vlan == (flow.source.vlanId ? flow.source.vlanId : innerVlanId) //due to issue 3475
            verifyEquals(it[0].arp.first(), arpData)
        }

        where:
        vlanId | innerVlanId | encapsulationType
        0      | 200         | FlowEncapsulationType.TRANSIT_VLAN
        100    | 200         | FlowEncapsulationType.TRANSIT_VLAN
        0      | 200         | FlowEncapsulationType.VXLAN
        100    | 200         | FlowEncapsulationType.VXLAN
    }

    @Tags([SMOKE_SWITCHES])
    def "Able to detect devices when two qinq single-switch different-port flows exist with the same outerVlanId"() {
        given: "Two flows between different ports on the same switch with the same outerVlanId"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")

        def sw = switches.all().withTraffGens().getListOfSwitches().find { it.traffGenPorts.size() > 1 }
        def tgsList = topology.getTraffGens().findAll { it.switchConnected.dpId == sw.switchId}
        assumeTrue(!tgsList.isEmpty(), "Wasn't able to find a switch with at least 2 trafGens")

        def flow1 = flowFactory.getSingleSwBuilder(sw, true)
                .withDetectedDevicesOnSrc(true, true)
                .withSourcePort(tgsList.first().switchPort)
                .withSourceVlan(commonOuterVlanId)
                .withSourceInnerVlan(innerVlanIdFlow1)
                .withEncapsulationType(encapsulationType)
                .withDestinationPort(tgsList.last().switchPort).build()
                .create()

        def flow2 = flowFactory.getSingleSwBuilder(sw, true)
                .withDetectedDevicesOnSrc(true, true)
                .withSourcePort(tgsList.first().switchPort)
                .withSourceVlan(commonOuterVlanId)
                .withSourceInnerVlan(innerVlanIdFlow2)
                .withEncapsulationType(encapsulationType)
                .withDestinationPort(tgsList.last().switchPort).build()
                .create()

        when: "Device connects to src endpoint and send lldp and arp packets for flow1 only"
        def tgService = traffExamProvider.get()
        def sourceConnectedDevice = flow1.sourceConnectedDeviceExam(tgService)
        def lldpData = sourceConnectedDevice.sendLldp()
        def arpData = sourceConnectedDevice.sendArp()

        then: "LLDP and ARP connected devices are recognized for flow1"
        Wrappers.wait(WAIT_OFFSET) { //need some time for devices to appear
            verifyAll(flow1.retrieveConnectedDevices()) {
                it.source.lldp.size() == 1
                it.source.arp.size() == 1
                it.destination.lldp.empty
                it.destination.arp.empty
                verifyEquals(it.source.lldp[0], lldpData)
                verifyEquals(it.source.arp[0], arpData)
            }
        }

        and: "LLDP and ARP connected devices are not recognized for flow2"
        verifyAll(flow2.retrieveConnectedDevices()) {
            it.source.lldp.empty
            it.source.arp.empty
            it.destination.lldp.empty
            it.destination.arp.empty
        }

        and: "Devices are registered on the switch"
        verifyAll(sw.getConnectedDevices().ports) {
            it.size() == 1
            it[0].portNumber == flow1.source.portNumber
            it[0].lldp.size() == 1
            it[0].lldp.first().flowId == flow1.flowId
            it[0].lldp.first().vlan == commonOuterVlanId //due to issue 3475
            verifyEquals(it[0].lldp.first(), lldpData)
            it[0].arp.size() == 1
            it[0].arp.first().flowId == flow1.flowId
            it[0].arp.first().vlan == commonOuterVlanId //due to issue 3475
            verifyEquals(it[0].arp.first(), arpData)
        }

        where:
        commonOuterVlanId | innerVlanIdFlow1 | innerVlanIdFlow2 | encapsulationType
        1                 | 2                | 4                | FlowEncapsulationType.TRANSIT_VLAN
        1                 | 3                | 5                | FlowEncapsulationType.VXLAN
    }

    def "Switch is not containing extra rules after connected devices removal"() {
        when: "A switch with devices feature turned on"
        assumeTrue(topology.activeTraffGens.size() > 0, "Require at least 1 switch with connected traffgen")
        def swPair = switchPairs.all().withTraffgensOnBothEnds().random()

        def initialProps = swPair.src.getCachedProps()
        swPair.src.updateProperties(initialProps.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        then: "The appropriate switch-related ARP/LLDP rules+meters are installed on switch"
        verifyAll(swPair.src.validate()) { validateResponse ->
            assert validateResponse.rules.asExpected && validateResponse.meters.asExpected

            [LLDP_TRANSIT_COOKIE, LLDP_INGRESS_COOKIE, LLDP_INPUT_PRE_DROP_COOKIE].each { lldpCookie ->
                assert validateResponse.rules.proper.findAll { it.cookie == lldpCookie }.size() == 1
                assert validateResponse.meters.proper.findAll { it.meterId == createMeterIdForDefaultRule(lldpCookie).value }.size() == 1
            }

            [ARP_TRANSIT_COOKIE, ARP_INGRESS_COOKIE, ARP_INPUT_PRE_DROP_COOKIE].each { arpCookie ->
                assert validateResponse.rules.proper.findAll { it.cookie == arpCookie }.size() == 1
                assert validateResponse.meters.proper.findAll { it.meterId == createMeterIdForDefaultRule(arpCookie).value }.size() == 1
            }
        }

        and: "Flow is created on a target switch with devices feature 'off'"
        def flow = flowFactory.getBuilder(swPair)
                .withDetectedDevicesOnSrc(false, false)
                .withDetectedDevicesOnDst(false, false).build()
                .create()

        when: "Turn LLDP and ARP detection off on switch"
        swPair.src.updateProperties(initialProps.jacksonCopy().tap {
            it.switchLldp = false
            it.switchArp = false
        })

        and: "Flow has been updated successfully"
        flow.update(flow.tap { it.maximumBandwidth = flow.maximumBandwidth + 1 })

        then: "Check excess rules are not registered on device"
        !swPair.src.synchronizeAndCollectFixedDiscrepancies().isPresent()

        and: "The appropriate switch-related ARP/LLDP rules+meters are removed from switch"
        verifyAll(swPair.src.validate()) { validateResponse ->
            assert validateResponse.rules.asExpected && validateResponse.meters.asExpected

            [LLDP_TRANSIT_COOKIE, LLDP_INGRESS_COOKIE, LLDP_INPUT_PRE_DROP_COOKIE].each { lldpCookie ->
                assert validateResponse.rules.proper.findAll { it.cookie == lldpCookie }.isEmpty()
                assert validateResponse.meters.proper.findAll { it.meterId == createMeterIdForDefaultRule(lldpCookie).value }.isEmpty()
            }

            [ARP_TRANSIT_COOKIE, ARP_INGRESS_COOKIE, ARP_INPUT_PRE_DROP_COOKIE].each { arpCookie ->
                assert validateResponse.rules.proper.findAll { it.cookie == arpCookie }.isEmpty()
                assert validateResponse.meters.proper.findAll { it.meterId == createMeterIdForDefaultRule(arpCookie).value }.isEmpty()
            }
        }
    }

    def "System starts detect connected after device properties turned on"() {
        given: "A switch with devices feature turned off"
        assumeTrue(topology.activeTraffGens.size() > 1, "Require at least 2 switch with connected traffgen")
        def swPair = switchPairs.all().withTraffgensOnBothEnds().random()

        def initialPropsSource = swPair.src.getCachedProps()
        swPair.src.updateProperties(initialPropsSource.jacksonCopy().tap {
            it.switchLldp = false
            it.switchArp = false
        })

        and: "Flow is created on a target switch with devices feature 'off'"
        def outerVlan = 100
        def flow = flowFactory.getBuilder(swPair, true)
                .withSourceVlan(outerVlan)
                .withSourceInnerVlan(200)
                .withDetectedDevicesOnSrc(false, false)
                .withDetectedDevicesOnDst(false, false).build()
                .create()

        assert swPair.src.getConnectedDevices().ports.empty
        assert swPair.dst.getConnectedDevices().ports.empty

        when: "update device properties and send lldp and arp packets on flow"
        swPair.src.updateProperties(initialPropsSource.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        def tgService = traffExamProvider.get()
        //retrieving devices for both src/dst endpoints, but only specifying flow vlan(outerVlan) without inner_vlan
        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService, [outerVlan])
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService, [outerVlan])
        sourceConnectedDevice.sendLldp()
        destinationConnectedDevice.sendLldp()
        sourceConnectedDevice.sendArp()
        destinationConnectedDevice.sendArp()

        then: "Getting connecting devices show corresponding devices on src endpoint"
        Wrappers.wait(3) {
            //under usual condition system needs some time for devices to appear, that's why timeLoop is used here
            verifyAll(swPair.src.getConnectedDevices()) {
                !(it.ports.lldp.empty)
                !(it.ports.arp.empty)
            }
        }

        and: "No connected devices for flow"
        verifyAll(flow.retrieveConnectedDevices()) {
            it.source.lldp.isEmpty()
            it.destination.lldp.isEmpty()
            it.source.arp.isEmpty()
            it.destination.arp.isEmpty()
        }
    }

    def "System stops receiving statistics if config is changed to 'off'"() {
        given: "A switch with devices feature turned on"
        assumeTrue(topology.activeTraffGens.size() > 1, "Require at least 2 switch with connected traffgen")
        def swPair = switchPairs.all().withTraffgensOnBothEnds().random()
        def initialPropsSource = swPair.src.getCachedProps()
        swPair.src.updateProperties(initialPropsSource.jacksonCopy().tap {
            it.switchLldp = true
            it.switchArp = true
        })

        and: "Flow is created on a target switch with devices feature 'off'"
        def tgService = traffExamProvider.get()
        def outerVlan = 100
        def flow = flowFactory.getBuilder(swPair)
                .withSourceVlan(outerVlan)
                .withSourceInnerVlan(200)
                .withDetectedDevicesOnSrc(false, false)
                .withDetectedDevicesOnDst(false, false).build()
                .create()

        when: "update device properties and send lldp and arp packets on flow"
        swPair.src.updateProperties(initialPropsSource.jacksonCopy().tap {
            it.switchLldp = false
            it.switchArp = false
        })

        def sourceConnectedDevice = flow.sourceConnectedDeviceExam(tgService, [outerVlan])
        def destinationConnectedDevice = flow.destinationConnectedDeviceExam(tgService, [outerVlan])
        sourceConnectedDevice.sendLldp()
        destinationConnectedDevice.sendLldp()
        sourceConnectedDevice.sendArp()
        destinationConnectedDevice.sendArp()

        then: "Getting connecting devices doesn't show corresponding devices on src endpoint"
        Wrappers.timedLoop(3) {
            //under usual condition system needs some time for devices to appear, that's why timeLoop is used here
            verifyAll(swPair.src.getConnectedDevices()) {
                it.ports.lldp.empty
                it.ports.arp.empty
            }
        }
    }


    /**
     * Returns a potential flow for creation according to passed params.
     * Note that for 'oneSwitch' it will return a single-port single-switch flow. There is no ability to obtain
     * single-switch different-port flow via this method.
     */
    private FlowBuilder getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled, SwitchPair switchPair) {
        assert !(oneSwitch && protectedFlow), "Cannot create one-switch flow with protected path"
        FlowBuilder flowBuilder = null
        if (oneSwitch) {
            flowBuilder = flowFactory.getSingleSwBuilder(switchPair.src, true)
        } else {
            assert switchPair.src.switchId != switchPair.dst.switchId
            flowBuilder = flowFactory.getBuilder(switchPair, true).withProtectedPath(protectedFlow)
        }
        flowBuilder.withDetectedDevicesOnSrc(srcEnabled, srcEnabled)
        flowBuilder.withDetectedDevicesOnDst(dstEnabled, dstEnabled)
        return flowBuilder
    }

    private FlowBuilder getFlowWithConnectedDevices(
            boolean protectedFlow, boolean oneSwitch, boolean srcEnabled, boolean dstEnabled) {
        def tgSwPair = getUniqueSwitchPairs()?.first()
        assumeTrue(tgSwPair as boolean, "Unable to find a switchPair with traffgens for the requested flow arguments")
        getFlowWithConnectedDevices(protectedFlow, oneSwitch, srcEnabled, dstEnabled, tgSwPair)
    }


    private FlowBuilder getFlowWithConnectedDevices(ConnectedDeviceTestData testData) {
        getFlowWithConnectedDevices(testData.protectedFlow, testData.oneSwitch, testData.srcEnabled,
                testData.dstEnabled, testData.switchPair)
    }

    /**
     * Pick as little as possible amount of switch pairs to cover all unique switch models we have (only connected
     * to traffgens and lldp-enabled).
     */
    @Memoized
    List<SwitchPair> getUniqueSwitchPairs() {
        def tgSwitches = switches.all().withTraffGens().getListOfSwitches()
        def unpickedTgSwitches = tgSwitches.unique(false) { [it.description, it.nbFormat().hardware].sort() }
        List<SwitchPair> switchPairs = switchPairs.all().withTraffgensOnBothEnds().getSwitchPairs()
        def result = []
        while (!unpickedTgSwitches.empty) {
            def pair = switchPairs.sort(false) { switchPair ->
                //prioritize swPairs with unique traffgens on both sides
                [switchPair.src, switchPair.dst].count { SwitchExtended sw ->
                    !unpickedTgSwitches.contains(sw)
                }
            }.first()
            //pick first pair and then re-sort considering updated list of unpicked switches
            result << pair
            unpickedTgSwitches = unpickedTgSwitches - pair.src - pair.dst
        }
        return result
    }

    def verifyEquals(ConnectedDeviceDto device, LldpData lldp) {
        assert device.macAddress == lldp.macAddress
        assert device.chassisId == "Mac Addr: $lldp.chassisId" //for now TG sends it as hardcoded 'mac address' subtype
        assert device.portId == "Locally Assigned: $lldp.portNumber" //subtype also hardcoded for now on traffgen side
        assert device.ttl == lldp.timeToLive
        //other non-mandatory lldp fields are out of scope for now. Most likely they are not properly parsed
        return true
    }

    def verifyEquals(ConnectedDeviceDto device, ArpData arp) {
        assert device.macAddress == arp.srcMac
        assert device.ipAddress == arp.srcIpv4
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

    def verifyEquals(SwitchConnectedDeviceDto device, ArpData arp) {
        assert device.macAddress == arp.srcMac
        assert device.ipAddress == arp.srcIpv4
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
