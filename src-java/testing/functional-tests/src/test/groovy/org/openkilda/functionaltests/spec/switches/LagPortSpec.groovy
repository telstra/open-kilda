package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.LAGFactory
import org.openkilda.testing.service.traffexam.model.LacpData
import org.openkilda.testing.tools.ConnectedDevice

import static org.openkilda.functionaltests.helpers.model.SwitchFilter.HAS_CONNECTED_TRAFFGENS

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.model.MeterId.LACP_REPLY_METER_ID
import static org.openkilda.model.cookie.Cookie.DROP_SLOW_PROTOCOLS_LOOP_COOKIE
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.grpc.speaker.model.LogicalPortDto
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.grpc.LogicalPortType
import org.openkilda.model.FlowPathDirection
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.model.cookie.PortColourCookie
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.grpc.GrpcService
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/blob/develop/docs/design/LAG-for-ports/README.md")
@Narrative("Verify that flow can be created on a LAG port.")
@Tags(HARDWARE)
class LagPortSpec extends HealthCheckSpecification {
    public static final long LACP_METER_ID = LACP_REPLY_METER_ID.value
    public static final String LACP_COOKIE = Cookie.toString(DROP_SLOW_PROTOCOLS_LOOP_COOKIE)

    @Autowired
    @Shared
    GrpcService grpc

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Autowired
    @Shared
    LAGFactory lagFactory

    @Shared
    Integer lagOffset = 2000

    @Tidy
    def "Able to CRUD LAG port with lacp_reply=#lacpReply on #sw.hwSwString"() {
        given: "A switch"
        def portsArrayCreate = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def portsArrayUpdate = topology.getAllowedPortsForSwitch(sw)[1, -1] as Set<Integer>
        assert portsArrayCreate.sort() != portsArrayUpdate.sort()

        when: "Create a LAG"
        def payloadCreate = new LagPortRequest(portNumbers: portsArrayCreate, lacpReply: lacpReply)
        def createResponse = northboundV2.createLagLogicalPort(sw.dpId, payloadCreate)

        then: "Response reports successful creation of the LAG port"
        with(createResponse) {
            logicalPortNumber > 0
            portNumbers.sort() == portsArrayCreate.sort()
            it.lacpReply == lacpReply
        }
        def lagPort = createResponse.logicalPortNumber

        and: "LAG port is really created"
        def getResponse = northboundV2.getLagLogicalPort(sw.dpId)
        getResponse.size() == 1
        with(getResponse[0]) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArrayCreate.sort()
        }

        and: "LAG port is really created on the switch(check GRPC)"
        def swAddress = northbound.getSwitch(sw.dpId).address
        with(grpc.getSwitchLogicalPortConfig(swAddress, lagPort)) {
            logicalPortNumber == lagPort
            name == "novi_lport" + lagPort.toString()
            portNumbers.sort() == portsArrayCreate.sort()
            type == LogicalPortType.LAG
        }

        and: "Switch is valid"
        with(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            it.verifyMeterSectionsAreEmpty()
        }

        when: "Update the LAG port"
        def payloadUpdate = new LagPortRequest(portNumbers: portsArrayUpdate)
        def updateResponse = northboundV2.updateLagLogicalPort(sw.dpId, lagPort, payloadUpdate)

        then: "Response reports successful update of the LAG port"
        with(updateResponse) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArrayUpdate.sort()
        }

        and: "LAG port is really updated"
        with(northboundV2.getLagLogicalPort(sw.dpId)) {
            it.size() == 1
            it[0].logicalPortNumber == lagPort
            it[0].portNumbers.sort() == portsArrayUpdate.sort()
        }

        and: "LAG port is really updated on the switch(check GRPC)"
        with(grpc.getSwitchLogicalPortConfig(swAddress, lagPort)) {
            logicalPortNumber == lagPort
            name == "novi_lport" + lagPort.toString()
            portNumbers.sort() == portsArrayUpdate.sort()
            type == LogicalPortType.LAG
        }

        and: "Switch is valid"
        with(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            it.verifyMeterSectionsAreEmpty()
            it.verifyLogicalPortsSectionsAreEmpty()
        }

        when: "Delete the LAG port"
        def deleteResponse = northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)

        then: "Response reports successful deletion of the LAG port"
        with(deleteResponse) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArrayUpdate.sort()
        }

        and: "LAG port is really deleted from db"
        northboundV2.getLagLogicalPort(sw.dpId).empty
        def lagPortIsDeleted = true

        and: "LAG port is really deleted from switch"
        !grpc.getSwitchLogicalPorts(swAddress).find { it.logicalPortNumber == lagPort }

        cleanup:
        lagPort && !lagPortIsDeleted && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)

        where:
        [sw, lacpReply] << [
                 getTopology().getActiveSwitches().unique(false) { it.hwSwString }, // switches
                 [false, true] // lacp reply
                ].combinations()
    }

    @Tidy
    def "Able to create a flow on a LAG port"() {
        given: "A switchPair with a LAG port on the src switch"
        def allTraffGenSwitchIds = topology.activeTraffGens*.switchConnected*.dpId
        assumeTrue(allTraffGenSwitchIds.size() > 1, "Unable to find required switches in topology")
        def switchPair = topologyHelper.getSwitchPairs().find {
            [it.src, it.dst].every { it.dpId in allTraffGenSwitchIds }
        }
        def traffgenSrcSwPort = switchPair.src.traffGens.switchPort[0]
        def portsArray = (topology.getAllowedPortsForSwitch(switchPair.src)[-2, -1] << traffgenSrcSwPort).unique()
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(switchPair.src.dpId, payload).logicalPortNumber

        when: "Create a flow"
        def flow = flowHelperV2.randomFlow(switchPair, true).tap { source.portNumber = lagPort }
        flowHelperV2.addFlow(flow)

        then: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "System allows traffic on the flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flowHelperV2.toV1(flow.tap { source.portNumber = traffgenSrcSwPort }), 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        lagPort && northboundV2.deleteLagLogicalPort(switchPair.src.dpId, lagPort)
    }

    @Tidy
    def "Able to create a singleSwitchFlow on a LAG port"() {
        given: "A switch with two traffgens and one LAG port"
        and: "A flow on the LAG port"
        def allTraffGenSwitchIds = topology.activeTraffGens*.switchConnected*.dpId
        assumeTrue(allTraffGenSwitchIds.size() > 1, "Unable to find active traffgen")
        def swPair = topologyHelper.getAllSingleSwitchPairs().find {
            it.src.dpId in allTraffGenSwitchIds && it.src.traffGens.size() > 1
        }
        assumeTrue(swPair.asBoolean(), "Unable to find required switch in topology")
        def traffgenSrcSwPort = swPair.src.traffGens[0].switchPort
        def traffgenDstSwPort = swPair.src.traffGens[1].switchPort
        def payload = new LagPortRequest(portNumbers: [traffgenSrcSwPort])
        def lagPort = northboundV2.createLagLogicalPort(swPair.src.dpId, payload).logicalPortNumber

        when: "Create a flow"
        def flow = flowHelperV2.singleSwitchFlow(swPair).tap {
            source.portNumber = lagPort
            destination.portNumber = traffgenDstSwPort
        }
        flowHelperV2.addFlow(flow)

        then: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "System allows traffic on the flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flowHelperV2.toV1(flow.tap { source.portNumber = traffgenSrcSwPort }), 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        lagPort && northboundV2.deleteLagLogicalPort(swPair.src.dpId, lagPort)
    }

    @Tidy
    def "LAG port is not deleted after switch reconnecting"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2, -1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(sw.dpId, payload).logicalPortNumber

        when: "Disconnect the switch"
        and: "Connect the switch back"
        def blockData = switchHelper.knockoutSwitch(sw, RW)
        def swIsActivated = false
        switchHelper.reviveSwitch(sw, blockData, true)
        swIsActivated = true

        then: "The LAG port is still exist"
        with(northboundV2.getLagLogicalPort(sw.dpId)[0]) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArray.sort()
        }

        and: "Switch is valid"
        with(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            it.verifyMeterSectionsAreEmpty()
        }

        cleanup:
        blockData && !swIsActivated && switchHelper.reviveSwitch(sw, blockData, true)
        lagPort && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)
    }

    @Tidy
    def "Unable to delete a LAG port in case flow on it"() {
        given: "A flow on a LAG port"
        def switchPair = topologyHelper.getSwitchPairs().first()
        def portsArray = topology.getAllowedPortsForSwitch(switchPair.src)[-2, -1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(switchPair.src.dpId, payload).logicalPortNumber
        def flow = flowHelperV2.randomFlow(switchPair).tap { source.portNumber = lagPort }
        flowHelperV2.addFlow(flow)

        when: "When delete LAG port"
        northboundV2.deleteLagLogicalPort(switchPair.src.dpId, lagPort)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Error during LAG delete"
        errorDetails.errorDescription == "Couldn't delete LAG port '$lagPort' from switch $switchPair.src.dpId " +
                "because flows '[$flow.flowId]' use it as endpoint"

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        lagPort && northboundV2.deleteLagLogicalPort(switchPair.src.dpId, lagPort)
    }

    @Tidy
    def "Unable to create LAG on a port with flow on it"() {
        given: "Active switch with flow on it"
        def sw = topology.activeSwitches.first()
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(flow)

        when: "Create a LAG port with flow's port"
        northboundV2.createLagLogicalPort(sw.dpId, new LagPortRequest(portNumbers: [flow.source.portNumber]))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Error during LAG create"
        errorDetails.errorDescription == "Physical port $flow.source.portNumber already used by following flows:" +
                " [$flow.flowId]. You must remove these flows to be able to use the port in LAG."

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        !exc && deleteAllLagPorts(sw.dpId)
    }

    @Tidy
    def "Unable to create a flow on port which is inside LAG group"() {
        given: "An active switch with LAG port on it"
        def sw = topology.activeSwitches.first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2, -1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(sw.dpId, payload).logicalPortNumber

        when: "Create flow on ports which are in inside LAG group"
        def flow = flowHelperV2.singleSwitchFlow(sw).tap {
            source.portNumber = portsArray[0]
            destination.portNumber = portsArray[1]
        }
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Port $flow.source.portNumber on switch $sw.dpId is used " +
                "as part of LAG port $lagPort"

        cleanup:
        !exc && flow && flowHelperV2.deleteFlow(flow.flowId)
        lagPort && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)

    }

    @Tidy
    def "Unable to create a LAG port with port which is used as mirrorPort"() {
        given: "A flow with mirrorPoint"
        def swP = topologyHelper.getNeighboringSwitchPair()
        def flow = flowHelperV2.randomFlow(swP, false)
        flowHelperV2.addFlow(flow)

        def mirrorPort = topology.getAllowedPortsForSwitch(swP.src).last()
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.FORWARD.toString().toLowerCase())
                .mirrorPointSwitchId(swP.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swP.src.dpId).portNumber(mirrorPort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        when: "Create a LAG port with port which is used as mirrorPort"
        northboundV2.createLagLogicalPort(swP.src.dpId, new LagPortRequest(portNumbers: [mirrorPort]))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Error during LAG create"
        errorDetails.errorDescription == "Physical port $mirrorPort already used as sink by following mirror points" +
                " flow '$flow.flowId': [$mirrorEndpoint.mirrorPointId]"

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        !exc && swP && deleteAllLagPorts(swP.src.dpId)
    }

    @Tidy
    def "Unable to create a LAG port in case port is #data.description"() {
        when: "Create a LAG port on a occupied port"
        def sw = topology.getActiveServer42Switches().first()
        def occupiedPort = data.portNumber(sw)
        northboundV2.createLagLogicalPort(sw.dpId, new LagPortRequest(portNumbers: [occupiedPort]))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Error during LAG create"
        errorDetails.errorDescription.contains(String.format(data.errorMsg, occupiedPort, sw.dpId))

        cleanup:
        !exc && deleteAllLagPorts(sw.dpId)

        where:
        data << [
                [
                        description: "occupied by server42",
                        portNumber : { Switch s -> s.prop.server42Port },
                        errorMsg   : "Physical port number %d on switch %s is server42 port."
                ],
                [
                        description: "occupied by isl",
                        portNumber : { Switch s -> getTopology().getBusyPortsForSwitch(s)[0] },
                        errorMsg   : "Physical port number %d intersects with existing ISLs"
                ],
                [
                        description: "more than lagOffset",
                        portNumber : { 2008 },
                        errorMsg   : "Physical port number %d can't be greater than LAG port offset $lagOffset."
                ],
                [
                        description: "not exist",
                        portNumber : { Switch s -> s.maxPort + 1 },
                        errorMsg   : "Invalid portno value."
                ]
        ]
    }

    @Tidy
    def "Unable to create two LAG ports with the same physical port inside at the same time"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def availablePorts = topology.getAllowedPortsForSwitch(sw)
        def portsArray = availablePorts[-2, -1]
        def conflictPortsArray = availablePorts[-3, -1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(sw.dpId, payload).logicalPortNumber

        when: "Try to create the same LAG port with the same physical ports inside"
        northboundV2.createLagLogicalPort(sw.dpId, new LagPortRequest(portNumbers: conflictPortsArray))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        //test errorMessage, conflictPortsArray was introduced
        errorDetails.errorMessage == "Error during LAG create"
        errorDetails.errorDescription == "Physical ports [${portsArray[-1]}] on switch $sw.dpId already " +
                "occupied by other LAG group(s)."

        cleanup:
        lagPort && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)
    }

    @Tidy
    def "Unable to proceed incorrect delete LAG port request (#data.description)"() {
        when: "Send invalid delete LAG port request"
        getNorthboundV2().deleteLagLogicalPort(data.swIdForRequest(), data.logicalPortNumber)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Error during LAG delete"
        errorDetails.errorDescription == String.format(data.errorMsg, data.swIdForRequest())

        where:
        data << [
                [
                        description      : "non-existent LAG port",
                        swIdForRequest   : { getTopology().getActiveSwitches().first().dpId },
                        logicalPortNumber: 1999, // lagOffset - 1
                        errorMsg         : "LAG port 1999 on switch %s not found"
                ],
                [
                        description      : "non-existent switch",
                        swIdForRequest   : { NON_EXISTENT_SWITCH_ID },
                        logicalPortNumber: 2001, // lagOffset + 1
                        errorMsg         : "Switch '%s' not found"
                ]
        ]
    }

    @Tidy
    def "System is able to detect and sync missed LAG port"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2,-1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(sw.dpId, payload).logicalPortNumber

        when: "Delete LAG port via grpc"
        grpc.deleteSwitchLogicalPort(northbound.getSwitch(sw.dpId).address, lagPort)

        then: "System detects that LAG port is missed"
        def lagPortMissingInfo = northbound.validateSwitch(sw.dpId).logicalPorts.missing
        lagPortMissingInfo.size() == 1
        with (lagPortMissingInfo[0]) {
            type == LogicalPortType.LAG.toString()
            logicalPortNumber == lagPort
            physicalPorts.sort() == portsArray.sort()
        }

        when: "Synchronize the switch"
        northbound.synchronizeSwitch(sw.dpId, false)

        then: "LAG port is reinstalled"
        northbound.validateSwitch(sw.dpId).logicalPorts.missing.empty

        cleanup:
        lagPort && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)
    }

    @Tidy
    def "System is able to detect misconfigured LAG port"() {
        //system can't re-install misconfigured LAG port
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-3,-1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(sw.dpId, payload).logicalPortNumber

        when: "Modify LAG port via grpc(delete, create with incorrect ports)"
        def swAddress = northbound.getSwitch(sw.dpId).address
        grpc.deleteSwitchLogicalPort(swAddress, lagPort)
        def request = new LogicalPortDto(LogicalPortType.LAG, [portsArray[0]], lagPort)
        grpc.createLogicalPort(swAddress, request)

        then: "System detects misconfigured LAG port"
        !northbound.validateSwitch(sw.dpId).logicalPorts.misconfigured.empty

        cleanup:
        lagPort && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)
    }

    @Tidy
    def "Able to create/update LAG port with duplicated port numbers on the #sw.hwSwString switch"() {
        given: "Switch and two ports"
        def sw = getTopology().getActiveSwitches().get(0)
        def testPorts = topology.getAllowedPortsForSwitch(sw).take(2)
        assert testPorts.size > 1

        when: "Create LAG port with duplicated port numbers"
        def switchPortToCreate = testPorts.get(0)
        def swAddress = northbound.getSwitch(sw.dpId).address
        def portListToCreate = [switchPortToCreate, switchPortToCreate]
        def createPayload = new LagPortRequest(portNumbers: portListToCreate)
        def lagPortCreateResponse = northboundV2.createLagLogicalPort(sw.dpId, createPayload)

        then: "Response shows that LAG port created successfully"
        with(lagPortCreateResponse) {
            logicalPortNumber > 0
            portNumbers == [switchPortToCreate]
        }
        def lagPort = lagPortCreateResponse.logicalPortNumber

        and: "Request on user side shows that LAG port created"
        with(northboundV2.getLagLogicalPort(sw.dpId)[0]) {
            logicalPortNumber == lagPort
            portNumbers == [switchPortToCreate]
        }

        and: "Created port exists in a list of all LAG ports from switch side (GRPC)"
        with(grpc.getSwitchLogicalPortConfig(swAddress, lagPort)) {
            logicalPortNumber == lagPort
            name == "novi_lport" + lagPort.toString()
            portNumbers == [switchPortToCreate]
            type == LogicalPortType.LAG
        }

        when: "Update the LAG port with duplicated port numbers"
        def switchPortToUpdate = testPorts.get(1)
        def portListToUpdate = [switchPortToUpdate, switchPortToUpdate]
        def updatePayload = new LagPortRequest(portNumbers: portListToUpdate)
        def lagPortUpdateResponse = northboundV2.updateLagLogicalPort(sw.dpId, lagPort, updatePayload)

        then: "Response shows that LAG port updated successfully"
        with(lagPortUpdateResponse) {
            logicalPortNumber == lagPort
            portNumbers == [switchPortToUpdate]
        }

        and: "Check on user side that LAG port updated successfully"
        with(northboundV2.getLagLogicalPort(sw.dpId)[0]) {
            logicalPortNumber == lagPort
            portNumbers == [switchPortToUpdate]
        }

        and: "Check that LAG port updated successfully on switch side (via GRPC)"
        with(grpc.getSwitchLogicalPortConfig(swAddress, lagPort)) {
            logicalPortNumber == lagPort
            name == "novi_lport" + lagPort.toString()
            portNumbers == [switchPortToUpdate]
            type == LogicalPortType.LAG
        }

        cleanup:
        lagPort && northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)
    }

    @Tidy
    def "Able to create and delete single LAG port with lacp_reply=#data.portLacpReply"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()
        def portsArrayCreate = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>

        when: "Create a LAG port"
        def createResponse = northboundV2.createLagLogicalPort(
                sw.dpId, new LagPortRequest(portsArrayCreate, data.portLacpReply))

        then: "Response reports successful creation of the LAG port"
        with(createResponse) {
            logicalPortNumber > 0
            portNumbers.sort() == portsArrayCreate.sort()
            lacpReply == data.portLacpReply
        }
        def portNumber = createResponse.logicalPortNumber

        and: "Correct rules and meters are on the switch"
        assertSwitchHasCorrectLacpRulesAndMeters(
                sw, data.mustContainCookies(portNumber), data.mustNotContainCookies(portNumber), data.mustContainLacpMeter)

        when: "Delete the LAG port"
        def deleteResponse = northboundV2.deleteLagLogicalPort(sw.dpId, portNumber)

        then: "Response reports successful delete of the LAG port"
        with(deleteResponse) {
            logicalPortNumber == portNumber
            portNumbers.sort() == portsArrayCreate.sort()
            lacpReply == data.portLacpReply
        }

        and: "No LACP rules and meters on the switch"
        assertSwitchHasCorrectLacpRulesAndMeters(sw, [], [LACP_COOKIE, getLagCookie(portNumber)], false)

        cleanup: "Remove all LAG ports"
        deleteAllLagPorts(sw.dpId)

        where:
        data << [
                [
                        portLacpReply : false,
                        mustContainCookies : { int port -> [] },
                        mustNotContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustContainLacpMeter : false
                ],
                [
                        portLacpReply : true,
                        mustContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustNotContainCookies : { int port -> [] },
                        mustContainLacpMeter : true
                ]
        ]
    }

    @Tidy
    def "Able to create and delete LAG port with #data.description"() {
        given: "A switch with LAG port"
        def sw = topology.getActiveSwitches().first()
        def physicalPortsOfLag1 = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def physicalPortsOfLag2 = topology.getAllowedPortsForSwitch(sw)[-4, -3] as Set<Integer>
        def portNumber1 = northboundV2.createLagLogicalPort(
                sw.dpId, new LagPortRequest(physicalPortsOfLag1, data.existingPortLacpReply)).logicalPortNumber

        when: "Create a LAG port"
        def createResponse = northboundV2.createLagLogicalPort(
                sw.dpId, new LagPortRequest(physicalPortsOfLag2, data.newPortLacpReply))

        then: "Response reports successful creation of the LAG port"
        with(createResponse) {
            logicalPortNumber > 0
            portNumbers.sort() == physicalPortsOfLag2.sort()
            lacpReply == data.newPortLacpReply
        }
        def portNumber2 = createResponse.logicalPortNumber

        and: "Correct rules and meters are on the switch"
        assertSwitchHasCorrectLacpRulesAndMeters(
                sw, data.mustContainCookies(portNumber1, portNumber2),
                data.mustNotContainCookies(portNumber1, portNumber2), data.mustContainLacpMeter)

        when: "Delete created LAG port"
        def deleteResponse = northboundV2.deleteLagLogicalPort(sw.dpId, portNumber2)

        then: "Response reports successful delete of the LAG port"
        with(deleteResponse) {
            logicalPortNumber == portNumber2
            portNumbers.sort() == physicalPortsOfLag2.sort()
            lacpReply == data.newPortLacpReply
        }

        and: "No LACP rules and meters of second LAG port on the switch"
        if (data.existingPortLacpReply) { // Switch must contain LACP rules and meter for first LAG port
            assertSwitchHasCorrectLacpRulesAndMeters(sw,
                    [LACP_COOKIE, getLagCookie(portNumber1)], [getLagCookie(portNumber2)], true)
        } else { // Switch must not contain any LACP rules and meter
            assertSwitchHasCorrectLacpRulesAndMeters(sw,
                    [], [LACP_COOKIE, getLagCookie(portNumber1), getLagCookie(portNumber2), ], false)
        }

        cleanup: "Remove all LAG ports"
        deleteAllLagPorts(sw.dpId)

        where:
        data << [
                [
                        description: "disabled LACP replies, near to LAG port with disabled LACP replies",
                        existingPortLacpReply : false,
                        newPortLacpReply : false,
                        mustContainCookies : { int oldPort, newPort -> [] },
                        mustNotContainCookies : { int oldPort, newPort -> [
                                LACP_COOKIE, getLagCookie(oldPort), getLagCookie(newPort)] },
                        mustContainLacpMeter : false
                ],
                [
                        description: "enabled LACP replies, near to LAG port with disabled LACP replies",
                        existingPortLacpReply : false,
                        newPortLacpReply : true,
                        mustContainCookies : { int oldPort, newPort -> [LACP_COOKIE, getLagCookie(newPort)] },
                        mustNotContainCookies : { int oldPort, newPort -> [getLagCookie(oldPort)] },
                        mustContainLacpMeter : true
                ],
                [
                        description: "disabled LACP replies, near to LAG port with enabled LACP replies",
                        existingPortLacpReply : true,
                        newPortLacpReply : false,
                        mustContainCookies : { int oldPort, newPort -> [LACP_COOKIE, getLagCookie(oldPort)] },
                        mustNotContainCookies : { int oldPort, newPort -> [getLagCookie(newPort)] },
                        mustContainLacpMeter : true
                ],
                [
                        description: "enabled LACP replies, near to LAG port with enabled LACP replies",
                        existingPortLacpReply : true,
                        newPortLacpReply : true,
                        mustContainCookies : { int oldPort, newPort -> [
                                LACP_COOKIE, getLagCookie(oldPort), getLagCookie(newPort)] },
                        mustNotContainCookies : { int oldPort, newPort -> [] },
                        mustContainLacpMeter : true
                ]
        ]
    }

    @Tidy
    def "Able to update #data.description for single LAG port"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()
        def physicalPortsOfCreatedLag = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def physicalPortsOfUpdatedLag = topology.getAllowedPortsForSwitch(sw)[-3, -2] as Set<Integer>

        and: "A LAG port"
        def createResponse = northboundV2.createLagLogicalPort(
                sw.dpId, new LagPortRequest(physicalPortsOfCreatedLag, data.oldlacpReply))
        with(createResponse) {
            assert logicalPortNumber > 0
            assert portNumbers.sort() == physicalPortsOfCreatedLag.sort()
        }
        def portNumber = createResponse.logicalPortNumber

        when: "Update the LAG port"
        def updatedPhysicalPorts = data.updatePorts ? physicalPortsOfUpdatedLag : physicalPortsOfCreatedLag
        def updateResponse = northboundV2.updateLagLogicalPort(
                sw.dpId, portNumber, new LagPortRequest(updatedPhysicalPorts, data.newlacpReply))

        then: "Response reports successful update of the LAG port"
        with(updateResponse) {
            logicalPortNumber == portNumber
            portNumbers.sort() == updatedPhysicalPorts.sort()
            lacpReply == data.newlacpReply
        }

        and: "Correct rules and meters are on the switch"
        assertSwitchHasCorrectLacpRulesAndMeters(
                sw, data.mustContainCookies(portNumber), data.mustNotContainCookies(portNumber), data.mustContainLacpMeter)

        cleanup: "Remove all LAG ports"
        deleteAllLagPorts(sw.dpId)

        where:
        data << [
                [
                        description: "physical ports of LAG with disabled LACP",
                        oldlacpReply : false,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { int port -> [] },
                        mustNotContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "physical ports of LAG with enabled LACP",
                        oldlacpReply : true,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustNotContainCookies : { int port -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from false to true, physical ports are same",
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustNotContainCookies : { int port -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false, physical ports are same",
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: false,
                        mustContainCookies : { int port -> [] },
                        mustNotContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "lacp_reply from false to true and update physical ports",
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: true,
                        mustContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustNotContainCookies : { int port -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false and update physical ports",
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { int port -> [] },
                        mustNotContainCookies : { int port -> [LACP_COOKIE, getLagCookie(port)] },
                        mustContainLacpMeter : false,
                ]
        ]
    }

    @Tidy
    def "Able to update #data.description near to existing LAG port with lacp_reply=#data.existingPortLacpReply"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()
        def physicalPortsOfLag1 = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def physicalPortsOfCreatedLag2 = topology.getAllowedPortsForSwitch(sw)[-4, -3] as Set<Integer>
        def physicalPortsOfUpdatedLag2 = topology.getAllowedPortsForSwitch(sw)[-5, -4] as Set<Integer>

        and: "LAG port 1"
        def portNumber1 = northboundV2.createLagLogicalPort(
                sw.dpId, new LagPortRequest(physicalPortsOfLag1, data.existingPortLacpReply)).logicalPortNumber

        and: "LAG port 2"
        def createResponse = northboundV2.createLagLogicalPort(
                sw.dpId, new LagPortRequest(physicalPortsOfCreatedLag2, data.oldlacpReply))
        with(createResponse) {
            assert logicalPortNumber > 0
            assert portNumbers.sort() == physicalPortsOfCreatedLag2.sort()
            assert lacpReply == data.oldlacpReply
        }
        def portNumber2 = createResponse.logicalPortNumber

        when: "Update the LAG port"
        def updatedPhysicalPorts = data.updatePorts ? physicalPortsOfUpdatedLag2 : physicalPortsOfCreatedLag2
        def updateResponse = northboundV2.updateLagLogicalPort(
                sw.dpId, portNumber2, new LagPortRequest(updatedPhysicalPorts, data.newlacpReply))

        then: "Response reports successful update of the LAG port"
        with(updateResponse) {
            logicalPortNumber == portNumber2
            portNumbers.sort() == updatedPhysicalPorts.sort()
            lacpReply == data.newlacpReply
        }

        and: "Correct rules and meters are on the switch"
        assertSwitchHasCorrectLacpRulesAndMeters(
                sw, data.mustContainCookies(portNumber1, portNumber2),
                data.mustNotContainCookies(portNumber1, portNumber2), data.mustContainLacpMeter)

        cleanup: "Remove all LAG ports"
        deleteAllLagPorts(sw.dpId)

        where:
        data << [
                [
                        description: "physical ports of LAG with disabled LACP",
                        existingPortLacpReply : false,
                        oldlacpReply : false,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { int port1, port2 -> [] },
                        mustNotContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port1), getLagCookie(port2)] },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "physical ports of LAG with enabled LACP",
                        existingPortLacpReply : false,
                        oldlacpReply : true,
                        newlacpReply : true,
                        updatePorts: true,
                        mustContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port2)] },
                        mustNotContainCookies : { int port1, port2 -> [getLagCookie(port1)] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from false to true",
                        existingPortLacpReply : false,
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port2)] },
                        mustNotContainCookies : { int port1, port2 -> [getLagCookie(port1)] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false",
                        existingPortLacpReply : false,
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: false,
                        mustContainCookies : { int port1, port2 -> [] },
                        mustNotContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port1), getLagCookie(port2)] },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "physical ports of LAG with disabled LACP",
                        existingPortLacpReply : true,
                        oldlacpReply : false,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port1)] },
                        mustNotContainCookies : { int port1, port2 -> [getLagCookie(port2)] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "physical ports of LAG with enabled LACP",
                        existingPortLacpReply : true,
                        oldlacpReply : true,
                        newlacpReply : true,
                        updatePorts: true,
                        mustContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port1), getLagCookie(port2)] },
                        mustNotContainCookies : { int port1, port2 -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from false to true",
                        existingPortLacpReply : true,
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port1), getLagCookie(port2)] },
                        mustNotContainCookies : { int port1, port2 -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false",
                        existingPortLacpReply : true,
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: false,
                        mustContainCookies : { int port1, port2 -> [LACP_COOKIE, getLagCookie(port1)] },
                        mustNotContainCookies : { int port1, port2 -> [getLagCookie(port2)] },
                        mustContainLacpMeter : true,
                ]
        ]
    }

    private void assertSwitchHasCorrectLacpRulesAndMeters(
            Switch sw, mustContainCookies, mustNotContainsCookies, mustContainLacpMeter) {
        // validate switch
        with(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            it.verifyMeterSectionsAreEmpty()
            it.verifyLogicalPortsSectionsAreEmpty()
        }

        // check cookies
        def hexCookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.collect { Cookie.toString(it) }
        assert hexCookies.containsAll(mustContainCookies)
        assert hexCookies.intersect(mustNotContainsCookies).isEmpty()

        // check meters
        def meters = northbound.getAllMeters(sw.dpId).meterEntries*.meterId
        if (mustContainLacpMeter) {
            assert LACP_REPLY_METER_ID.value in meters
        } else {
            assert LACP_REPLY_METER_ID.value !in meters
        }
    }

    @Tidy
    def "Unable decrease bandwidth on LAG port lower than connected flows bandwidth sum"() {
        given: "Flows on a LAG port with switch ports"
        def switchPair = topologyHelper.getSwitchPairs().first()
        def testPorts = topology.getAllowedPortsForSwitch(switchPair.src).takeRight(2).sort()
        assert testPorts.size > 1
        def maximumBandwidth = testPorts.sum { northbound.getPort(switchPair.src.dpId, it).currentSpeed }
        def payload = new LagPortRequest(portNumbers: testPorts)
        def lagPort = northboundV2.createLagLogicalPort(switchPair.src.dpId, payload).logicalPortNumber
        def flow = flowHelperV2.randomFlow(switchPair).tap {
            source.portNumber = lagPort
            it.maximumBandwidth = maximumBandwidth
        }
        flowHelperV2.addFlow(flow)

        when: "Decrease LAG port bandwidth by deleting one port to make it lower than connected flows bandwidth sum"
        def updatePayload = new LagPortRequest(portNumbers: [testPorts.get(0)])
        northboundV2.updateLagLogicalPort(switchPair.src.dpId, lagPort, updatePayload)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Error processing LAG logical port #$lagPort on $switchPair.src.dpId update request"
        errorDetails.errorDescription == "Not enough bandwidth for LAG port $lagPort."

        then: "No bandwidth changed for LAG port and all connected ports are in place"
        with(northboundV2.getLagLogicalPort(switchPair.src.dpId)[0]) {
            logicalPortNumber == lagPort
            portNumbers == testPorts
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        lagPort && northboundV2.deleteLagLogicalPort(switchPair.src.dpId, lagPort)
    }

    @Tidy
    def "Able to delete LAG port if it is already removed from switch"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2,-1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = northboundV2.createLagLogicalPort(sw.dpId, payload).logicalPortNumber

        when: "Delete LAG port via grpc"
        grpc.deleteSwitchLogicalPort(northbound.getSwitch(sw.dpId).address, lagPort)

        then: "Able to delete LAG port from switch with no exception"
        def deleteResponse = northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)

        with(deleteResponse) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArray.sort()
        }
    }

    @Tidy
    def "Able to retrieve actual LACP connection status on LAG port"() {
        given: "A switch with a connected traffgen"
        def sw = topology.getActiveSwitches().findAll(HAS_CONNECTED_TRAFFGENS).shuffled().first()
        def traffGen = sw.getTraffGens().first()

        and: "LAG port on a traffgen port"
        def lag = lagFactory.get(sw, traffGen.getSwitchPort(), true)
        lag.create()

        and: "LACP data example"
        def lacpData = LacpData.builder()
        .expired(true)
        .defaulted(false)
        .distributing(true)
        .collecting(false)
        .synchronization(true)
        .aggregation(false)
        .lacpTimeout(true)
        .lacpActivity(false)
        .build()

        when: "Send LACP dataunit to switch LAG port"
        def connectedDevice = new ConnectedDevice(traffExamProvider.get(), traffGen, [100])
        connectedDevice.sendLacp(lacpData)

        then: "Information from LACP dataunit is available LACP status response"
        lacpData == lag.getLacpData()

        cleanup:
        Wrappers.silent{
            lag.delete()
            connectedDevice.close()
        }
    }

    def getLagCookie(portNumber) {
        new PortColourCookie(CookieType.LACP_REPLY_INPUT, portNumber).toString()
    }

    void deleteAllLagPorts(SwitchId switchId) {
        northboundV2.getLagLogicalPort(switchId)*.logicalPortNumber.each { Integer lagPort ->
            northboundV2.deleteLagLogicalPort(switchId, lagPort)
        }
        assert northboundV2.getLagLogicalPort(switchId).empty
    }
}
