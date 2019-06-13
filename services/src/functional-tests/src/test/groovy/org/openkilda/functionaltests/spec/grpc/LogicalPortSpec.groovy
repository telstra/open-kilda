package org.openkilda.functionaltests.spec.grpc

import org.openkilda.grpc.speaker.model.LogicalPortDto
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.SwitchInfoData
import org.openkilda.messaging.model.grpc.LogicalPortType

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""This test suite checks the CRUD actions on a logical port.
Logical ports are defined by associating a single physical port to them to define
Bidirectional Forwarding Detection(BFD) ports or
by associating a list of physical ports to them to create Link Aggregation Groups (LAG) or
a list of BFD ports to them to create a LAG for fast-failover for BFD sessions.

NOTE: The GRPC implementation supports the LAG type only and it is set by default.""")
class LogicalPortSpec extends GrpcBaseSpecification {

    @Unroll
    def "Able to create/read/delete logicalport on the #sw.switchId switch"() {
        /**the update action is not working(issue on a Noviflow switch side)*/
        when: "Create logical port"
        def switchPort = topology.getAllowedPortsForSwitch(topology.find(sw.switchId))[0]
        def switchLogicalPort = 2000 + switchPort
        def request = new LogicalPortDto(LogicalPortType.BFD, [switchPort], switchLogicalPort)
        def responseAfterCreating = grpc.createLogicalPort(sw.address, request)
        def portExists = true
        //TODO(rtretiak): should be refactored accodring to #2491 when fixed
        assert responseAfterCreating.logicalPortNumber == switchLogicalPort
        assert responseAfterCreating.type == LogicalPortType.BFD

        then: "Able to get the created logical port"
        def responseAfterGetting = grpc.getSwitchLogicalPortConfig(sw.address, switchLogicalPort)
        responseAfterGetting.logicalPortNumber == switchLogicalPort
        responseAfterGetting.name == "novi_lport" + switchLogicalPort.toString()
        responseAfterGetting.portNumbers[0] == switchPort

        and: "The created port is exist in a list of all logical port"
        grpc.getSwitchLogicalPorts(sw.address).contains(responseAfterGetting)

        //        TODO(andriidovhan): add update action
        //        and: "able to edit the created logical port"
        when: "Try to delete the created logical port"
        def responseAfterDeleting = grpc.deleteSwitchLogicalPort(sw.address, switchLogicalPort)

        then: "Logical port is deleted"
        responseAfterDeleting.deleted

        when: "Try to get the deleted logical port"
        portExists = false
        grpc.getSwitchLogicalPortConfig(sw.address, switchLogicalPort)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage == "Provided logical port does not exist."

        cleanup: "Remove created port"
        portExists && grpc.deleteSwitchLogicalPort(sw.address, switchLogicalPort)

        where:
        sw << getNoviflowSwitches("6.5")
    }

    @Unroll
    def "Not able to create logical port with incorrect port number(lPort/sPort): \
#data.logicalPortNumber/#data.portNumber on the #sw.switchId switch"(Map data, SwitchInfoData sw) {
        when:
        "Try to create logical port: #logicalPortNumber/#portNumber"
        def switchPort = topology.getAllowedPortsForSwitch(topology.find(sw.switchId))[0]
        def switchLogicalPort = 2000 + switchPort
        def pNumber = data.portNumber ? data.portNumber : switchPort
        def lPortNumber = data.logicalPortNumber ? data.logicalPortNumber : switchLogicalPort
        grpc.createLogicalPort(sw.address, new LogicalPortDto(LogicalPortType.LAG, [pNumber], lPortNumber))

        then: "Human readable error is returned."
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == data.errorMessage

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
                ], getNoviflowSwitches("6.5")].combinations()
    }

    @Unroll
    def "Not able to delete non-existent logical port number on the #sw.switchId switch"() {
        when: "Try to delete incorrect logicalPortNumber"
        Integer fakeNumber = 12345
        grpc.deleteSwitchLogicalPort(sw.address, fakeNumber)

        then: "Human readable error is returned."
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage == "Provided logical port does not exist."

        where:
        sw << getNoviflowSwitches("6.4")
    }
}
