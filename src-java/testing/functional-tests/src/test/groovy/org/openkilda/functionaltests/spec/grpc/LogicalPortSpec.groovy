package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.env.EnvType.VIRTUAL_ENV

import org.openkilda.functionaltests.error.LogicalPortNotCreatedExpectedError
import org.openkilda.functionaltests.error.LogicalPortNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.grpc.speaker.model.LogicalPortDto
import org.openkilda.messaging.model.grpc.LogicalPortType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER

@Narrative("""This test suite checks the CRUD actions on a logical port.
Logical ports are defined by associating a single physical port to them to define
Bidirectional Forwarding Detection(BFD) ports or
by associating a list of physical ports to them to create Link Aggregation Groups (LAG) or
a list of BFD ports to them to create a LAG for fast-failover for BFD sessions.

NOTE: The GRPC implementation supports the LAG type only and it is set by default.""")
class LogicalPortSpec extends GrpcBaseSpecification {
    @Autowired @Shared
    CleanupManager cleanupManager

    def "Able to create/read/delete logicalport on the #sw.hwSwString switch"() {
        when: "Create logical port"
        def switchPort
        if (profile == VIRTUAL_ENV.value) {
            switchPort = (Math.random()*100).toInteger()
        } else {
            switchPort = northbound.getPorts(sw.switchId).find {
                it.state[0] == "LINK_DOWN" && !it.name.contains("novi_lport")
            }.portNumber
        }

        def switchLogicalPort = 1100 + switchPort
        def request = new LogicalPortDto(LogicalPortType.BFD, [switchPort], switchLogicalPort)
        cleanupManager.addAction(OTHER, {grpc.deleteSwitchLogicalPort(sw.address, switchLogicalPort)})
        def responseAfterCreating = grpc.createLogicalPort(sw.address, request)
        assert responseAfterCreating.logicalPortNumber.value == switchLogicalPort

        then: "Able to get the created logical port"
        def responseAfterGetting = grpc.getSwitchLogicalPortConfig(sw.address, switchLogicalPort)
        responseAfterGetting.logicalPortNumber == switchLogicalPort
        responseAfterGetting.name == "novi_lport" + switchLogicalPort.toString()
        responseAfterGetting.portNumbers[0] == switchPort
        responseAfterCreating.type == LogicalPortType.BFD

        and: "The created port is exist in a list of all logical port"
        grpc.getSwitchLogicalPorts(sw.address).contains(responseAfterGetting)

        when: "Try to delete the created logical port"
        def responseAfterDeleting = grpc.deleteSwitchLogicalPort(sw.address, switchLogicalPort)

        then: "Logical port is deleted"
        responseAfterDeleting.deleted

        when: "Try to get the deleted logical port"
        grpc.getSwitchLogicalPortConfig(sw.address, switchLogicalPort)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LogicalPortNotFoundExpectedError()

        where:
        sw << getNoviflowSwitches()
    }

    @Tags(HARDWARE)
    def "Not able to create logical port with incorrect port number(lPort/sPort): \
#data.logicalPortNumber/#data.portNumber on the #sw.hwSwString switch"() {
        when:
        "Try to create logical port: #logicalPortNumber/#portNumber"
        def switchPort = northbound.getPorts(sw.switchId).find { it.state[0] == "LINK_DOWN" }.portNumber
        def switchLogicalPort = 1100 + switchPort
        def pNumber = data.portNumber ? data.portNumber : switchPort
        def lPortNumber = data.logicalPortNumber ? data.logicalPortNumber : switchLogicalPort
        grpc.createLogicalPort(sw.address, new LogicalPortDto(LogicalPortType.LAG, [pNumber], lPortNumber))

        then: "Human readable error is returned."
        def exc = thrown(HttpClientErrorException)
        new LogicalPortNotCreatedExpectedError(data.errorMessage, ~/.*/).matches(exc)

        where:
        [data, sw] << [
                [
                        [logicalPortNumber: 99,
                         portNumber       : false,
                         errorMessage     : "Valid logicalportno range is 100 to 63487."],
                        [logicalPortNumber: 63488,
                         portNumber       : false,
                         errorMessage     : "Valid logicalportno range is 100 to 63487."],
                        [logicalPortNumber: false,
                         portNumber       : 44444,
                         errorMessage     : "Invalid portno value."],
                ], noviflowSwitches].combinations()
    }

    @Tags(HARDWARE)
    def "Not able to delete non-existent logical port number on the #sw.hwSwString switch"() {
        when: "Try to delete incorrect logicalPortNumber"
        /** info from manual:
         *  Value between 100 and 63487except for the NS-21100 where the value must be between 113 and 63487
         *  and for the WB-5000 and SM-5000 Series where the value must be between 1000 and 63487.
        */
        def validLogicalPorts = 1000..63487
        def busyLogicalPorts = grpc.getSwitchLogicalPorts(sw.address)*.logicalPortNumber.sort()
        Integer nonExistentLogicalPort = (validLogicalPorts - busyLogicalPorts).first()
        grpc.deleteSwitchLogicalPort(sw.address, nonExistentLogicalPort)

        then: "Human readable error is returned."
        def exc = thrown(HttpClientErrorException)
        new LogicalPortNotFoundExpectedError(~/.*/).matches(exc)
        where:
        sw << getNoviflowSwitches()
    }
}
