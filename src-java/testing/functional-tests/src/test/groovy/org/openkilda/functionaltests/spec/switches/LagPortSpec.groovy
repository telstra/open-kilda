package org.openkilda.functionaltests.spec.switches

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.getLagCookies
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.randomVlan
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
import org.openkilda.functionaltests.helpers.model.LagPort
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.grpc.speaker.model.LogicalPortDto
import org.openkilda.messaging.model.grpc.LogicalPortType
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.testing.service.grpc.GrpcService
import org.openkilda.testing.service.traffexam.TraffExamService

import jakarta.inject.Provider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/blob/develop/docs/design/LAG-for-ports/README.md")
@Narrative("Verify that flow can be created on a LAG port.")
@Tags([HARDWARE])
class LagPortSpec extends HealthCheckSpecification {

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

    def "Able to CRUD LAG port with lacp_reply=#lacpReply on #sw.hwSwString()"(SwitchExtended sw, boolean lacpReply) {
        given: "A switch"
        def portsArrayCreate = sw.getPorts()[-2, -1] as Set<Integer>
        def portsArrayUpdate = sw.getPorts()[1, -1] as Set<Integer>
        assert portsArrayCreate.sort() != portsArrayUpdate.sort()

        when: "Create a LAG"
        def lagPort = sw.getLagPort(portsArrayCreate).create(lacpReply)

        then: "Response reports successful creation of the LAG port"
        verifyAll(lagPort) {
            logicalPortNumber > 0
            portNumbers.sort() == portsArrayCreate.sort()
            it.lacpReply == lacpReply
        }

        and: "LAG port is really created"
        def getResponse = sw.getAllLogicalPorts()
        getResponse.size() == 1
        verifyAll(getResponse[0]) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == portsArrayCreate.sort()
        }

        and: "LAG port is really created on the switch(check GRPC)"
        def swAddress = sw.getDetails().address
        verifyAll(grpc.getSwitchLogicalPortConfig(swAddress, lagPort.logicalPortNumber)) {
            logicalPortNumber == lagPort.logicalPortNumber
            name == "novi_lport" + lagPort.logicalPortNumber.toString()
            portNumbers.sort() == portsArrayCreate.sort()
            type == LogicalPortType.LAG
        }

        and: "Switch is valid"
        !sw.validateAndCollectFoundDiscrepancies().isPresent()

        when: "Update the LAG port"
        def payloadUpdate = new LagPortRequest(portNumbers: portsArrayUpdate)
        def updateLagPort = lagPort.update(payloadUpdate)

        then: "Response reports successful updation of the LAG port"
        verifyAll(updateLagPort) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == portsArrayUpdate.sort()
        }

        and: "LAG port is really updated"
        verifyAll(sw.getAllLogicalPorts()) {
            it.size() == 1
            it[0].logicalPortNumber == lagPort.logicalPortNumber
            it[0].portNumbers.sort() == portsArrayUpdate.sort()
        }

        and: "LAG port is really updated on the switch(check GRPC)"
        verifyAll(grpc.getSwitchLogicalPortConfig(swAddress, lagPort.logicalPortNumber)) {
            logicalPortNumber == lagPort.logicalPortNumber
            name == "novi_lport" + lagPort.logicalPortNumber.toString()
            portNumbers.sort() == portsArrayUpdate.sort()
            type == LogicalPortType.LAG
        }

        and: "Switch is valid"
        !sw.validateAndCollectFoundDiscrepancies().isPresent()

        when: "Delete the LAG port"
        def deleteResponse = lagPort.delete()

        then: "Response reports successful deletion of the LAG port"
        verifyAll(deleteResponse) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == portsArrayUpdate.sort()
        }

        and: "LAG port is really deleted from db"
        sw.getAllLogicalPorts().empty

        and: "LAG port is really deleted from switch"
        !grpc.getSwitchLogicalPorts(swAddress).find { it.logicalPortNumber == lagPort.logicalPortNumber }

        where:
        [sw, lacpReply] << [
                 switches.all().uniqueByHw(), // switches
                 [false, true] // lacp reply
                ].combinations()
    }

    def "Able to create a flow on a LAG port"() {
        given: "A switchPair with a LAG port on the src switch"
        def switchPair = switchPairs.all().withTraffgensOnBothEnds().random()
        def traffgenSrcSwPort = switchPair.src.traffGenPorts.first()
        def portsArray = (switchPair.src.getPorts()[-2, -1] + [traffgenSrcSwPort]) as Set<Integer>
        def lagPortNumber = switchPair.src.getLagPort(portsArray).create().logicalPortNumber

        when: "Create a flow"
        def flow = flowFactory.getBuilder(switchPair)
                .withSourcePort(lagPortNumber)
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
        Integer traffgenSrcSwPort = swPair.src.traffGenPorts.first()
        Integer traffgenDstSwPort = swPair.src.traffGenPorts.last()
        def lagPortNumber = swPair.src.getLagPort([traffgenSrcSwPort] as Set<Integer>).create().logicalPortNumber

        when: "Create a flow"
        def flow = flowFactory.getBuilder(swPair)
                .withSourcePort(lagPortNumber)
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
        def sw = switches.all().random()
        def portsArray = sw.getPorts()[-2, -1]
        def lagPort = sw.getLagPort(portsArray as Set).create()

        when: "Disconnect the switch"
        def blockData = sw.knockout(RW)

        and: "Connect the switch back"
        sw.revive(blockData, true)

        then: "The LAG port is still exist"
        with(sw.getAllLogicalPorts()[0]) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == portsArray.sort()
        }

        and: "Switch is valid"
        !sw.validateAndCollectFoundDiscrepancies().isPresent()
    }

    def "Unable to delete a LAG port in case flow on it"() {
        given: "A flow on a LAG port"
        def switchPair = switchPairs.all().random()
        def portsArray = switchPair.src.getPorts()[-2, -1]
        def lagPort = switchPair.src.getLagPort(portsArray as Set).create()
        def flow = flowFactory.getBuilder(switchPair).withSourcePort(lagPort.logicalPortNumber).build().create()

        when: "When delete LAG port"
        lagPort.sendDeleteRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotDeletedExpectedError(~/Couldn\'t delete LAG port \'$lagPort.logicalPortNumber\' from switch $switchPair.src.switchId \
because flows \'\[$flow.flowId\]\' use it as endpoint/).matches(exc)
    }

    def "Unable to create LAG on a port with flow on it"() {
        given: "Active switch with flow on it"
        def sw = switches.all().random()
        def flow = flowFactory.getSingleSwRandom(sw)

        when: "Create a LAG port with flow's port"
        sw.getLagPort([flow.source.portNumber] as Set).create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(~/Physical port $flow.source.portNumber already used by following flows:\
 \[$flow.flowId\]. You must remove these flows to be able to use the port in LAG./).matches(exc)
    }

    def "Unable to create a flow on port which is inside LAG group"() {
        given: "An active switch with LAG port on it"
        def sw = switches.all().random()
        def portsArray = sw.getPorts()[-2, -1]
        def flowSourcePort = portsArray[0]
        def lagPort = sw.getLagPort(portsArray as Set).create()

        when: "Create flow on ports which are in inside LAG group"
        flowFactory.getBuilder(sw, sw)
                .withSourcePort(flowSourcePort)
                .withDestinationPort(portsArray[1])
                .build().sendCreateRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Port $flowSourcePort \
on switch $sw.switchId is used as part of LAG port $lagPort.logicalPortNumber/).matches(exc)

    }

    def "Unable to create a LAG port with port which is used as mirrorPort"() {
        given: "A flow with mirrorPoint"
        def swP = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(swP, false)
        def mirrorPort = swP.src.getPorts().last()
        def mirrorEndpoint = flow.createMirrorPoint(swP.src.switchId, mirrorPort, randomVlan())

        when: "Create a LAG port with port which is used as mirrorPort"
        swP.src.getLagPort([mirrorPort] as Set).create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(~/Physical port $mirrorPort already used as sink by following mirror points flow\
 \'${flow.getFlowId()}\'\: \[${mirrorEndpoint.getMirrorPointId()}\]/).matches(exc)

    }

    def "Unable to create a LAG port in case port is #data.description"() {
        when: "Create a LAG port on a occupied port"
        def sw = switches.all().withS42Support().first()
        def occupiedPort = data.portNumber(sw)
        sw.getLagPort([occupiedPort] as Set).create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(data.errorDescription).matches(exc)

        where:
        data << [
                [
                        description: "occupied by server42",
                        portNumber : { SwitchExtended swExtended -> swExtended.sw.prop.server42Port },
                        errorDescription: ~/Physical port number \d+ on switch .*? is server42 port./
                ],
                [
                        description: "occupied by isl",
                        portNumber : { SwitchExtended swExtended -> swExtended.getIslPorts()[0] },
                        errorDescription: ~/Physical port number \d+ intersects with existing ISLs/
                ],
                [
                        description: "more than lagOffset",
                        portNumber : { 2008 },
                        errorDescription: ~/Physical port number \d+ can\'t be greater than LAG port offset $lagOffset./
                ],
                [
                        description: "not exist",
                        portNumber : { SwitchExtended swExtended -> swExtended.sw.maxPort + 1 },
                        errorDescription: ~/Invalid portno value./
                ]
        ]
    }

    def "Unable to create two LAG ports with the same physical port inside at the same time"() {
        given: "A switch with a LAG port"
        def sw = switches.all().random()
        def availablePorts = sw.getPorts()
        def portsArray = availablePorts[-2, -1]
        def conflictPortsArray = availablePorts[-3, -1]
        sw.getLagPort(portsArray as Set).create()

        when: "Try to create the same LAG port with the same physical ports inside"
        sw.getLagPort(conflictPortsArray as Set).create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotCreatedExpectedError(~/Physical ports \[${portsArray[-1]}]\ on switch $sw.switchId already \
occupied by other LAG group\(s\)./).matches(exc)
    }

    def "Unable to proceed incorrect delete LAG port request (#data.description)"() {
        when: "Send invalid delete LAG port request"
        northboundV2.deleteLagLogicalPort(data.swIdForRequest(), data.logicalPortNumber)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotDeletedWithNotFoundExpectedError(data.errorDescription).matches(exc)

        where:
        data << [
                [
                        description      : "non-existent LAG port",
                        swIdForRequest   : { topology.switches.dpId.first() },
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
        def sw = switches.all().random()
        def portsArray = sw.getPorts()[-2,-1]
        def lagPort = sw.getLagPort(portsArray as Set).create()

        when: "Delete LAG port via grpc"
        grpc.deleteSwitchLogicalPort(sw.getDetails().address, lagPort.logicalPortNumber)

        then: "System detects that LAG port is missed"
        def lagPortMissingInfo = sw.validate().logicalPorts.missing
        lagPortMissingInfo.size() == 1
        verifyAll(lagPortMissingInfo[0]) {
            type == LogicalPortType.LAG.toString()
            logicalPortNumber == lagPort.logicalPortNumber
            physicalPorts.sort() == portsArray.sort()
        }

        when: "Synchronize the switch"
        sw.synchronizeAndCollectFixedDiscrepancies()

        then: "LAG port is reinstalled"
        !sw.validateAndCollectFoundDiscrepancies().isPresent()
    }

    def "System is able to detect misconfigured LAG port"() {
        //system can't re-install misconfigured LAG port
        given: "A switch with a LAG port"
        def sw = switches.all().random()
        def portsArray = sw.getPorts()[-3,-1]
        def lagPort = sw.getLagPort(portsArray as Set).create()

        when: "Modify LAG port via grpc(delete, create with incorrect ports)"
        def swAddress = sw.getDetails().address
        grpc.deleteSwitchLogicalPort(swAddress, lagPort.logicalPortNumber)
        def request = new LogicalPortDto(LogicalPortType.LAG, [portsArray[0]], lagPort.logicalPortNumber)
        grpc.createLogicalPort(swAddress, request)

        then: "System detects misconfigured LAG port"
        !sw.validate().logicalPorts.misconfigured.empty
    }

    def "Able to create/update LAG port with duplicated port numbers on the switch"() {
        given: "Switch and two ports"
        def sw = switches.all().random()
        def testPorts = sw.getPorts().take(2)
        assert testPorts.size > 1

        when: "Create LAG port with duplicated port numbers"
        def switchPortToCreate = testPorts.first()
        def swAddress = sw.getDetails().address
        def portListToCreate = [switchPortToCreate, switchPortToCreate]
        def lagPort = sw.getLagPort(portListToCreate as Set).create()

        then: "Response shows that LAG port created successfully"
        verifyAll(lagPort) {
            logicalPortNumber > 0
            portNumbers == [switchPortToCreate] as Set
        }

        and: "Request on user side shows that LAG port created"
        verifyAll(sw.getAllLogicalPorts()[0]) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers == [switchPortToCreate]
        }

        and: "Created port exists in a list of all LAG ports from switch side (GRPC)"
        verifyAll(grpc.getSwitchLogicalPortConfig(swAddress, lagPort.logicalPortNumber)) {
            logicalPortNumber == lagPort.logicalPortNumber
            name == "novi_lport" + lagPort.logicalPortNumber.toString()
            portNumbers == [switchPortToCreate]
            type == LogicalPortType.LAG
        }

        when: "Update the LAG port with duplicated port numbers"
        def switchPortToUpdate = testPorts.get(1)
        def portListToUpdate = [switchPortToUpdate, switchPortToUpdate]
        def updatePayload = new LagPortRequest(portNumbers: portListToUpdate)
        def updatedLagPort = lagPort.update(updatePayload)

        then: "Response shows that LAG port updated successfully"
        verifyAll(updatedLagPort) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers == [switchPortToUpdate] as Set
        }

        and: "Check on user side that LAG port updated successfully"
        verifyAll(sw.getAllLogicalPorts()[0]) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers == [switchPortToUpdate]
        }

        and: "Check that LAG port updated successfully on switch side (via GRPC)"
        verifyAll(grpc.getSwitchLogicalPortConfig(swAddress, lagPort.logicalPortNumber)) {
            logicalPortNumber == lagPort.logicalPortNumber
            name == "novi_lport" + lagPort.logicalPortNumber.toString()
            portNumbers == [switchPortToUpdate]
            type == LogicalPortType.LAG
        }
    }

    def "Able to create and delete single LAG port with lacp_reply=#data.portLacpReply"() {
        given: "A switch"
        def sw = switches.all().random()
        def portsArrayCreate = sw.getPorts()[-2, -1] as Set<Integer>

        when: "Create a LAG port"
        def lagPort = sw.getLagPort(portsArrayCreate).create(data.portLacpReply)

        then: "Response reports successful creation of the LAG port"
        verifyAll(lagPort) {
            logicalPortNumber > 0
            portNumbers.sort() == portsArrayCreate.sort()
            lacpReply == data.portLacpReply
        }

        and: "Correct rules and meters are on the switch"
        sw.verifyLacpRulesAndMeters(data.mustContainCookies(lagPort),
                data.mustNotContainCookies(lagPort), data.mustContainLacpMeter)

        when: "Delete the LAG port"
        def deleteResponse = lagPort.delete()

        then: "Response reports successful delete of the LAG port"
        verifyAll(deleteResponse) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == portsArrayCreate.sort()
            lacpReply == data.portLacpReply
        }

        and: "No LACP rules and meters on the switch"
        sw.verifyLacpRulesAndMeters([], getLagCookies([lagPort], true), false)

        where:
        data << [
                [
                        portLacpReply : false,
                        mustContainCookies : { LagPort port -> [] },
                        mustNotContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustContainLacpMeter : false
                ],
                [
                        portLacpReply : true,
                        mustContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustNotContainCookies : { LagPort port -> [] },
                        mustContainLacpMeter : true
                ]
        ]
    }

    def "Able to create and delete LAG port with #data.description"() {
        given: "A switch with LAG port"
        def sw = switches.all().random()
        def physicalPortsOfLag1 = sw.getPorts()[-2, -1] as Set<Integer>
        def physicalPortsOfLag2 = sw.getPorts()[-4, -3] as Set<Integer>
        def initialLagPort = sw.getLagPort(physicalPortsOfLag1).create(data.existingPortLacpReply)

        when: "Create a LAG port"
        def lagPort = sw.getLagPort(physicalPortsOfLag2).create(data.newPortLacpReply)

        then: "Response reports successful creation of the LAG port"
        verifyAll(lagPort) {
            logicalPortNumber > 0
            portNumbers.sort() == physicalPortsOfLag2.sort()
            lacpReply == data.newPortLacpReply
        }

        and: "Correct rules and meters are on the switch"
        sw.verifyLacpRulesAndMeters(data.mustContainCookies(initialLagPort, lagPort),
                data.mustNotContainCookies(initialLagPort, lagPort), data.mustContainLacpMeter)

        when: "Delete created LAG port"
        def deleteResponse = lagPort.delete()

        then: "Response reports successful delete of the LAG port"
        verifyAll(deleteResponse) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == physicalPortsOfLag2.sort()
            lacpReply == data.newPortLacpReply
        }

        and: "No LACP rules and meters of second LAG port on the switch"
        if (data.existingPortLacpReply) { // Switch must contain LACP rules and meter for first LAG port
            sw.verifyLacpRulesAndMeters(getLagCookies([initialLagPort], true),
                    getLagCookies([lagPort], false),  true)

        } else { // Switch must not contain any LACP rules and meter
            sw.verifyLacpRulesAndMeters([],
                    getLagCookies([initialLagPort, lagPort], true), false)
        }

        where:
        data << [
                [
                        description: "disabled LACP replies, near to LAG port with disabled LACP replies",
                        existingPortLacpReply : false,
                        newPortLacpReply : false,
                        mustContainCookies : { LagPort oldPort, newPort -> [] },
                        mustNotContainCookies : { LagPort oldPort, newPort -> getLagCookies([oldPort, newPort], true) },
                        mustContainLacpMeter : false
                ],
                [
                        description: "enabled LACP replies, near to LAG port with disabled LACP replies",
                        existingPortLacpReply : false,
                        newPortLacpReply : true,
                        mustContainCookies : { LagPort oldPort, newPort -> getLagCookies([newPort], true) },
                        mustNotContainCookies : { LagPort oldPort, newPort -> getLagCookies([oldPort], false) },
                        mustContainLacpMeter : true
                ],
                [
                        description: "disabled LACP replies, near to LAG port with enabled LACP replies",
                        existingPortLacpReply : true,
                        newPortLacpReply : false,
                        mustContainCookies : { LagPort oldPort, newPort -> getLagCookies([oldPort], true) },
                        mustNotContainCookies : { LagPort oldPort, newPort -> getLagCookies([newPort], false) },
                        mustContainLacpMeter : true
                ],
                [
                        description: "enabled LACP replies, near to LAG port with enabled LACP replies",
                        existingPortLacpReply : true,
                        newPortLacpReply : true,
                        mustContainCookies : { LagPort oldPort, newPort -> getLagCookies([oldPort, newPort], true) },
                        mustNotContainCookies : { LagPort oldPort, newPort -> [] },
                        mustContainLacpMeter : true
                ]
        ]
    }

    def "Able to update #data.description for single LAG port"() {
        given: "A switch"
        def sw = switches.all().random()
        def physicalPortsOfCreatedLag = sw.getPorts()[-2, -1] as Set<Integer>
        def physicalPortsOfUpdatedLag = sw.getPorts()[-3, -2] as Set<Integer>

        and: "A LAG port"
        def lagPort = sw.getLagPort(physicalPortsOfCreatedLag).create(data.oldlacpReply)
        verifyAll(lagPort) {
            assert logicalPortNumber > 0
            assert portNumbers.sort() == physicalPortsOfCreatedLag.sort()
        }

        when: "Update the LAG port"
        def updatedPhysicalPorts = data.updatePorts ? physicalPortsOfUpdatedLag : physicalPortsOfCreatedLag
        def updatedLagPort = lagPort.update(new LagPortRequest(updatedPhysicalPorts, data.newlacpReply))

        then: "Response reports successful update of the LAG port"
        verifyAll(updatedLagPort) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == updatedPhysicalPorts.sort()
            lacpReply == data.newlacpReply
        }

        and: "Correct rules and meters are on the switch"
        sw.verifyLacpRulesAndMeters(data.mustContainCookies(lagPort),
                data.mustNotContainCookies(lagPort), data.mustContainLacpMeter)

        where:
        data << [
                [
                        description: "physical ports of LAG with disabled LACP",
                        oldlacpReply : false,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { LagPort port -> [] },
                        mustNotContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "physical ports of LAG with enabled LACP",
                        oldlacpReply : true,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustNotContainCookies : { LagPort port -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from false to true, physical ports are same",
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustNotContainCookies : { LagPort port -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false, physical ports are same",
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: false,
                        mustContainCookies : { LagPort port -> [] },
                        mustNotContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "lacp_reply from false to true and update physical ports",
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: true,
                        mustContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustNotContainCookies : { LagPort port -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false and update physical ports",
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { LagPort port -> [] },
                        mustNotContainCookies : { LagPort port -> getLagCookies([port], true) },
                        mustContainLacpMeter : false,
                ]
        ]
    }

    def "Able to update #data.description near to existing LAG port with lacp_reply=#data.existingPortLacpReply"() {
        given: "A switch"
        def sw = switches.all().random()
        def physicalPortsOfLag1 = sw.getPorts()[-2, -1] as Set<Integer>
        def physicalPortsOfCreatedLag2 = sw.getPorts()[-4, -3] as Set<Integer>
        def physicalPortsOfUpdatedLag2 = sw.getPorts()[-5, -4] as Set<Integer>

        and: "LAG port 1"
        def lagPort1 = sw.getLagPort(physicalPortsOfLag1).create(data.existingPortLacpReply)

        and: "LAG port 2"
        def lagPort2 = sw.getLagPort(physicalPortsOfCreatedLag2).create(data.oldlacpReply)
        verifyAll(lagPort2) {
            assert logicalPortNumber > 0
            assert portNumbers.sort() == physicalPortsOfCreatedLag2.sort()
            assert lacpReply == data.oldlacpReply
        }

        when: "Update the LAG port"
        def updatedPhysicalPorts = data.updatePorts ? physicalPortsOfUpdatedLag2 : physicalPortsOfCreatedLag2
        def updatedLagPort2 = lagPort2.update(new LagPortRequest(updatedPhysicalPorts, data.newlacpReply))

        then: "Response reports successful update of the LAG port"
        verifyAll(updatedLagPort2) {
            logicalPortNumber == lagPort2.logicalPortNumber
            portNumbers.sort() == updatedPhysicalPorts.sort()
            lacpReply == data.newlacpReply
        }

        and: "Correct rules and meters are on the switch"
        sw.verifyLacpRulesAndMeters(data.mustContainCookies(lagPort1, lagPort2),
                data.mustNotContainCookies(lagPort1, lagPort2), data.mustContainLacpMeter)

        where:
        data << [
                [
                        description: "physical ports of LAG with disabled LACP",
                        existingPortLacpReply : false,
                        oldlacpReply : false,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { LagPort port1, port2 -> [] },
                        mustNotContainCookies : { LagPort port1, port2 -> getLagCookies([port1, port2], true) },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "physical ports of LAG with enabled LACP",
                        existingPortLacpReply : false,
                        oldlacpReply : true,
                        newlacpReply : true,
                        updatePorts: true,
                        mustContainCookies : { LagPort port1, port2 -> getLagCookies([port2], true) },
                        mustNotContainCookies : { LagPort port1, port2 -> getLagCookies([port1], false) },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from false to true",
                        existingPortLacpReply : false,
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { LagPort port1, port2 -> getLagCookies([port2], true) },
                        mustNotContainCookies : { LagPort port1, port2 -> getLagCookies([port1], false)},
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false",
                        existingPortLacpReply : false,
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: false,
                        mustContainCookies : { LagPort port1, port2 -> [] },
                        mustNotContainCookies : { LagPort port1, port2 -> getLagCookies([port1, port2], true) },
                        mustContainLacpMeter : false,
                ],
                [
                        description: "physical ports of LAG with disabled LACP",
                        existingPortLacpReply : true,
                        oldlacpReply : false,
                        newlacpReply : false,
                        updatePorts: true,
                        mustContainCookies : { LagPort port1, port2 -> getLagCookies([port1], true) },
                        mustNotContainCookies : { LagPort port1, port2 -> getLagCookies([port2], false)},
                        mustContainLacpMeter : true,
                ],
                [
                        description: "physical ports of LAG with enabled LACP",
                        existingPortLacpReply : true,
                        oldlacpReply : true,
                        newlacpReply : true,
                        updatePorts: true,
                        mustContainCookies : { LagPort port1, port2 -> getLagCookies([port1, port2], true) },
                        mustNotContainCookies : { LagPort port1, port2 -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from false to true",
                        existingPortLacpReply : true,
                        oldlacpReply : false,
                        newlacpReply : true,
                        updatePorts: false,
                        mustContainCookies : { LagPort port1, port2 -> getLagCookies([port1, port2], true) },
                        mustNotContainCookies : { LagPort port1, port2 -> [] },
                        mustContainLacpMeter : true,
                ],
                [
                        description: "lacp_reply from true to false",
                        existingPortLacpReply : true,
                        oldlacpReply : true,
                        newlacpReply : false,
                        updatePorts: false,
                        mustContainCookies : { LagPort port1, port2 -> getLagCookies([port1], true) },
                        mustNotContainCookies : { LagPort port1, port2 -> getLagCookies([port2], false) },
                        mustContainLacpMeter : true,
                ]
        ]
    }

    def "Unable decrease bandwidth on LAG port lower than connected flows bandwidth sum"() {
        given: "Flows on a LAG port with switch ports"
        def switchPair = switchPairs.all().withoutWBSwitch().random()
        def testPorts = switchPair.src.getPorts().takeRight(2).sort()
        assert testPorts.size > 1
        def maximumBandwidth = testPorts.sum { switchPair.src.getPort(it).retrieveDetails().currentSpeed }
        def lagPort = switchPair.src.getLagPort(testPorts as Set).create()

        flowFactory.getBuilder(switchPair)
                .withSourcePort(lagPort.logicalPortNumber)
                .withBandwidth(maximumBandwidth as Long)
                .build().create()

        when: "Decrease LAG port bandwidth by deleting one port to make it lower than connected flows bandwidth sum"
        def updatePayload = new LagPortRequest(portNumbers: [testPorts.get(0)])
        lagPort.update(updatePayload)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LagNotUpdatedExpectedError(
                switchPair.src.switchId, lagPort.logicalPortNumber, ~/Not enough bandwidth for LAG port $lagPort.logicalPortNumber./).matches(exc)
        then: "No bandwidth changed for LAG port and all connected ports are in place"
        verifyAll(switchPair.src.getAllLogicalPorts()[0]) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers == testPorts
        }
    }

    def "Able to delete LAG port if it is already removed from switch"() {
        given: "A switch with a LAG port"
        def sw = switches.all().random()
        def portsArray = sw.getPorts()[-2,-1]
        def lagPort = sw.getLagPort(portsArray as Set).create()

        when: "Delete LAG port via grpc"
        grpc.deleteSwitchLogicalPort(sw.getDetails().address, lagPort.logicalPortNumber)

        then: "Able to delete LAG port from switch with no exception"
        def deleteResponse = lagPort.delete()
        verifyAll(deleteResponse) {
            logicalPortNumber == lagPort.logicalPortNumber
            portNumbers.sort() == portsArray.sort()
        }
    }
}
