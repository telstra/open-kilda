package org.openkilda.functionaltests.spec.switches

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.SwitchHelper.randomVlan
import static org.openkilda.model.MeterId.LACP_REPLY_METER_ID
import static org.openkilda.model.cookie.Cookie.DROP_SLOW_PROTOCOLS_LOOP_COOKIE
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.LagNotCreatedExpectedError
import org.openkilda.functionaltests.error.LagNotDeletedExpectedError
import org.openkilda.functionaltests.error.LagNotDeletedWithNotFoundExpectedError
import org.openkilda.functionaltests.error.LagNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.grpc.speaker.model.LogicalPortDto
import org.openkilda.messaging.model.grpc.LogicalPortType
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.model.cookie.PortColourCookie
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.grpc.GrpcService
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider


@See("https://github.com/telstra/open-kilda/blob/develop/docs/design/LAG-for-ports/README.md")
@Narrative("Verify that flow can be created on a LAG port.")
@Tags([HARDWARE])
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
    FlowFactory flowFactory
    @Shared
    Integer lagOffset = 2000

    def "Able to CRUD LAG port with lacp_reply=#lacpReply on #sw.hwSwString"() {
        given: "A switch"
        def portsArrayCreate = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def portsArrayUpdate = topology.getAllowedPortsForSwitch(sw)[1, -1] as Set<Integer>
        assert portsArrayCreate.sort() != portsArrayUpdate.sort()

        when: "Create a LAG"
        def createResponse = switchHelper.createLagLogicalPort(sw.dpId, portsArrayCreate, lacpReply)

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
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.dpId).isPresent()

        when: "Update the LAG port"
        def payloadUpdate = new LagPortRequest(portNumbers: portsArrayUpdate)
        def updateResponse = northboundV2.updateLagLogicalPort(sw.dpId, lagPort, payloadUpdate)

        then: "Response reports successful updation of the LAG port"
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
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.dpId).isPresent()

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

        where:
        [sw, lacpReply] << [
                 getTopology().getActiveSwitches().unique(false) { it.hwSwString }, // switches
                 [false, true] // lacp reply
                ].combinations()
    }

    def "Able to create a flow on a LAG port"() {
        given: "A switchPair with a LAG port on the src switch"
        def switchPair = switchPairs.all().withTraffgensOnBothEnds().random()
        def traffgenSrcSwPort = switchPair.src.traffGens.switchPort[0]
        def portsArray = (topology.getAllowedPortsForSwitch(switchPair.src)[-2, -1] << traffgenSrcSwPort).unique()
        def lagPort = switchHelper.createLagLogicalPort(switchPair.src.dpId, portsArray as Set).logicalPortNumber

        when: "Create a flow"
        def flow = flowFactory.getBuilder(switchPair)
                .withSourcePort(lagPort)
                .build().create()

        then: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        flow.pingAndCollectDiscrepancies().isEmpty()

        and: "System allows traffic on the flow"
        def traffExam = traffExamProvider.get()
        //the physical port with traffGen used for LAG port creation should be specified
        def exam = flow.deepCopy().tap{ source.portNumber = traffgenSrcSwPort }.traffExam(traffExam, 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    def "Able to create a singleSwitchFlow on a LAG port"() {
        given: "A switch with two traffgens and one LAG port"
        and: "A flow on the LAG port"
        def swPair = switchPairs.singleSwitch()
                .withAtLeastNTraffgensOnSource(2).random()
        def traffgenSrcSwPort = swPair.src.traffGens[0].switchPort
        def traffgenDstSwPort = swPair.src.traffGens[1].switchPort
        def lagPort = switchHelper.createLagLogicalPort(swPair.src.dpId, [traffgenSrcSwPort] as Set).logicalPortNumber

        when: "Create a flow"
        def flow = flowFactory.getBuilder(swPair)
                .withSourcePort(lagPort)
                .withDestinationPort(traffgenDstSwPort)
                .build().create()

        then: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "System allows traffic on the flow"
        def traffExam = traffExamProvider.get()
        //the physical port with traffGen used for LAG port creation should be specified
        def exam = flow.deepCopy().tap { source.portNumber = traffgenSrcSwPort }.traffExam(traffExam, 1000, 3)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "LAG port is not deleted after switch reconnecting"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2, -1]
        def lagPort = switchHelper.createLagLogicalPort(sw.dpId, portsArray as Set).logicalPortNumber

        when: "Disconnect the switch"
        and: "Connect the switch back"
        def blockData = switchHelper.knockoutSwitch(sw, RW)
        switchHelper.reviveSwitch(sw, blockData, true)

        then: "The LAG port is still exist"
        with(northboundV2.getLagLogicalPort(sw.dpId)[0]) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArray.sort()
        }

        and: "Switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.dpId).isPresent()
    }

    def "Unable to delete a LAG port in case flow on it"() {
        given: "A flow on a LAG port"
        def switchPair = switchPairs.all().random()
        def portsArray = topology.getAllowedPortsForSwitch(switchPair.src)[-2, -1]
        def lagPort = switchHelper.createLagLogicalPort(switchPair.src.dpId, portsArray as Set).logicalPortNumber
        def flow = flowFactory.getBuilder(switchPair).withSourcePort(lagPort).build().create()

        when: "When delete LAG port"
        northboundV2.deleteLagLogicalPort(switchPair.src.dpId, lagPort)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotDeletedExpectedError(~/Couldn\'t delete LAG port \'$lagPort\' from switch $switchPair.src.dpId \
because flows \'\[$flow.flowId\]\' use it as endpoint/).matches(exc)
    }

    def "Unable to create LAG on a port with flow on it"() {
        given: "Active switch with flow on it"
        def sw = topology.activeSwitches.first()
        def flow = flowFactory.getRandom(sw, sw)

        when: "Create a LAG port with flow's port"
        switchHelper.createLagLogicalPort(sw.dpId, [flow.source.portNumber] as Set)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(~/Physical port $flow.source.portNumber already used by following flows:\
 \[$flow.flowId\]. You must remove these flows to be able to use the port in LAG./).matches(exc)
    }

    def "Unable to create a flow on port which is inside LAG group"() {
        given: "An active switch with LAG port on it"
        def sw = topology.activeSwitches.first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2, -1]
        def flowSourcePort = portsArray[0]
        def lagPort = switchHelper.createLagLogicalPort(sw.dpId, portsArray as Set).logicalPortNumber

        when: "Create flow on ports which are in inside LAG group"
        flowFactory.getBuilder(sw, sw)
                .withSourcePort(flowSourcePort)
                .withDestinationPort(portsArray[1])
                .build().sendCreateRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Port $flowSourcePort \
on switch $sw.dpId is used as part of LAG port $lagPort/).matches(exc)

    }

    def "Unable to create a LAG port with port which is used as mirrorPort"() {
        given: "A flow with mirrorPoint"
        def swP = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(swP, false)
        def mirrorPort = topology.getAllowedPortsForSwitch(swP.src).last()
        def mirrorEndpoint = flow.createMirrorPoint(swP.src.dpId, mirrorPort, randomVlan())

        when: "Create a LAG port with port which is used as mirrorPort"
        switchHelper.createLagLogicalPort(swP.src.dpId, [mirrorPort] as Set)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(~/Physical port $mirrorPort already used as sink by following mirror points flow\
 \'${flow.getFlowId()}\'\: \[${mirrorEndpoint.getMirrorPointId()}\]/).matches(exc)

    }

    def "Unable to create a LAG port in case port is #data.description"() {
        when: "Create a LAG port on a occupied port"
        def sw = topology.getActiveServer42Switches().first()
        def occupiedPort = data.portNumber(sw)
        switchHelper.createLagLogicalPort(sw.dpId, [occupiedPort] as Set)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(data.errorDescription).matches(exc)

        where:
        data << [
                [
                        description: "occupied by server42",
                        portNumber : { Switch s -> s.prop.server42Port },
                        errorDescription: ~/Physical port number \d+ on switch .*? is server42 port./
                ],
                [
                        description: "occupied by isl",
                        portNumber : { Switch s -> getTopology().getBusyPortsForSwitch(s)[0] },
                        errorDescription: ~/Physical port number \d+ intersects with existing ISLs/
                ],
                [
                        description: "more than lagOffset",
                        portNumber : { 2008 },
                        errorDescription: ~/Physical port number \d+ can\'t be greater than LAG port offset $lagOffset./
                ],
                [
                        description: "not exist",
                        portNumber : { Switch s -> s.maxPort + 1 },
                        errorDescription: ~/Invalid portno value./
                ]
        ]
    }

    def "Unable to create two LAG ports with the same physical port inside at the same time"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def availablePorts = topology.getAllowedPortsForSwitch(sw)
        def portsArray = availablePorts[-2, -1]
        def conflictPortsArray = availablePorts[-3, -1]
        def payload = new LagPortRequest(portNumbers: portsArray)
        def lagPort = switchHelper.createLagLogicalPort(sw.dpId, portsArray as Set).logicalPortNumber

        when: "Try to create the same LAG port with the same physical ports inside"
        northboundV2.createLagLogicalPort(sw.dpId, new LagPortRequest(portNumbers: conflictPortsArray))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(~/Physical ports \[${portsArray[-1]}]\ on switch $sw.dpId already \
occupied by other LAG group\(s\)./).matches(exc)
    }

    def "Unable to proceed incorrect delete LAG port request (#data.description)"() {
        when: "Send invalid delete LAG port request"
        getNorthboundV2().deleteLagLogicalPort(data.swIdForRequest(), data.logicalPortNumber)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotDeletedWithNotFoundExpectedError(data.errorDescription).matches(exc)

        where:
        data << [
                [
                        description      : "non-existent LAG port",
                        swIdForRequest   : { getTopology().getActiveSwitches().first().dpId },
                        logicalPortNumber: 1999, // lagOffset - 1
                        errorDescription : ~/LAG port 1999 on switch .*? not found/
                ],
                [
                        description      : "non-existent switch",
                        swIdForRequest   : { NON_EXISTENT_SWITCH_ID },
                        logicalPortNumber: 2001, // lagOffset + 1
                        errorDescription : ~/Switch '.*' not found/
                ]
        ]
    }

    def "System is able to detect and sync missed LAG port"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2,-1]
        def lagPort = switchHelper.createLagLogicalPort(sw.dpId, portsArray as Set).logicalPortNumber

        when: "Delete LAG port via grpc"
        grpc.deleteSwitchLogicalPort(northbound.getSwitch(sw.dpId).address, lagPort)

        then: "System detects that LAG port is missed"
        def lagPortMissingInfo = switchHelper.validateAndCollectFoundDiscrepancies(sw.dpId).get().logicalPorts.missing
        lagPortMissingInfo.size() == 1
        with (lagPortMissingInfo[0]) {
            type == LogicalPortType.LAG.toString()
            logicalPortNumber == lagPort
            physicalPorts.sort() == portsArray.sort()
        }

        when: "Synchronize the switch"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.dpId)

        then: "LAG port is reinstalled"
        !switchHelper.validateAndCollectFoundDiscrepancies(sw.dpId).isPresent()
    }

    def "System is able to detect misconfigured LAG port"() {
        //system can't re-install misconfigured LAG port
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-3,-1]
        def lagPort = switchHelper.createLagLogicalPort(sw.dpId, portsArray as Set).logicalPortNumber

        when: "Modify LAG port via grpc(delete, create with incorrect ports)"
        def swAddress = northbound.getSwitch(sw.dpId).address
        grpc.deleteSwitchLogicalPort(swAddress, lagPort)
        def request = new LogicalPortDto(LogicalPortType.LAG, [portsArray[0]], lagPort)
        grpc.createLogicalPort(swAddress, request)

        then: "System detects misconfigured LAG port"
        !switchHelper.validateAndCollectFoundDiscrepancies(sw.dpId).get().logicalPorts.misconfigured.empty
    }

    def "Able to create/update LAG port with duplicated port numbers on the #sw.hwSwString switch"() {
        given: "Switch and two ports"
        def sw = getTopology().getActiveSwitches().get(0)
        def testPorts = topology.getAllowedPortsForSwitch(sw).take(2)
        assert testPorts.size > 1

        when: "Create LAG port with duplicated port numbers"
        def switchPortToCreate = testPorts.get(0)
        def swAddress = northbound.getSwitch(sw.dpId).address
        def portListToCreate = [switchPortToCreate, switchPortToCreate]
        def lagPortCreateResponse = switchHelper.createLagLogicalPort(sw.dpId, portListToCreate as Set)

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
    }

    def "Able to create and delete single LAG port with lacp_reply=#data.portLacpReply"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()
        def portsArrayCreate = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>

        when: "Create a LAG port"
        def createResponse = switchHelper.createLagLogicalPort(
                sw.dpId, portsArrayCreate, data.portLacpReply)

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

    def "Able to create and delete LAG port with #data.description"() {
        given: "A switch with LAG port"
        def sw = topology.getActiveSwitches().first()
        def physicalPortsOfLag1 = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def physicalPortsOfLag2 = topology.getAllowedPortsForSwitch(sw)[-4, -3] as Set<Integer>
        def portNumber1 = switchHelper.createLagLogicalPort(
                sw.dpId, physicalPortsOfLag1 as Set, data.existingPortLacpReply).logicalPortNumber

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

    def "Able to update #data.description for single LAG port"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()
        def physicalPortsOfCreatedLag = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def physicalPortsOfUpdatedLag = topology.getAllowedPortsForSwitch(sw)[-3, -2] as Set<Integer>

        and: "A LAG port"
        def createResponse = switchHelper.createLagLogicalPort(
                sw.dpId, physicalPortsOfCreatedLag, data.oldlacpReply)
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

    def "Able to update #data.description near to existing LAG port with lacp_reply=#data.existingPortLacpReply"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()
        def physicalPortsOfLag1 = topology.getAllowedPortsForSwitch(sw)[-2, -1] as Set<Integer>
        def physicalPortsOfCreatedLag2 = topology.getAllowedPortsForSwitch(sw)[-4, -3] as Set<Integer>
        def physicalPortsOfUpdatedLag2 = topology.getAllowedPortsForSwitch(sw)[-5, -4] as Set<Integer>

        and: "LAG port 1"
        def portNumber1 = switchHelper.createLagLogicalPort(
                sw.dpId, physicalPortsOfLag1, data.existingPortLacpReply).logicalPortNumber

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
        !switchHelper.validateAndCollectFoundDiscrepancies(sw.dpId).isPresent()

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

    def "Unable decrease bandwidth on LAG port lower than connected flows bandwidth sum"() {
        given: "Flows on a LAG port with switch ports"
        def switchPair = switchPairs.all().random()
        def testPorts = topology.getAllowedPortsForSwitch(switchPair.src).takeRight(2).sort()
        assert testPorts.size > 1
        def maximumBandwidth = testPorts.sum { northbound.getPort(switchPair.src.dpId, it).currentSpeed }
        def lagPort = switchHelper.createLagLogicalPort(switchPair.src.dpId, testPorts as Set).logicalPortNumber
        def flow = flowFactory.getBuilder(switchPair)
                .withSourcePort(lagPort)
                .withBandwidth(maximumBandwidth as Long)
                .build().create()

        when: "Decrease LAG port bandwidth by deleting one port to make it lower than connected flows bandwidth sum"
        def updatePayload = new LagPortRequest(portNumbers: [testPorts.get(0)])
        northboundV2.updateLagLogicalPort(switchPair.src.dpId, lagPort, updatePayload)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotUpdatedExpectedError(
                switchPair.getSrc().getDpId(), lagPort, ~/Not enough bandwidth for LAG port $lagPort./).matches(exc)
        then: "No bandwidth changed for LAG port and all connected ports are in place"
        with(northboundV2.getLagLogicalPort(switchPair.src.dpId)[0]) {
            logicalPortNumber == lagPort
            portNumbers == testPorts
        }
    }

    def "Able to delete LAG port if it is already removed from switch"() {
        given: "A switch with a LAG port"
        def sw = topology.getActiveSwitches().first()
        def portsArray = topology.getAllowedPortsForSwitch(sw)[-2,-1]
        def lagPort = switchHelper.createLagLogicalPort(sw.dpId, portsArray as Set).logicalPortNumber

        when: "Delete LAG port via grpc"
        grpc.deleteSwitchLogicalPort(northbound.getSwitch(sw.dpId).address, lagPort)

        then: "Able to delete LAG port from switch with no exception"
        def deleteResponse = northboundV2.deleteLagLogicalPort(sw.dpId, lagPort)

        with(deleteResponse) {
            logicalPortNumber == lagPort
            portNumbers.sort() == portsArray.sort()
        }
    }

    def getLagCookie(portNumber) {
        new PortColourCookie(CookieType.LACP_REPLY_INPUT, portNumber).toString()
    }
}
