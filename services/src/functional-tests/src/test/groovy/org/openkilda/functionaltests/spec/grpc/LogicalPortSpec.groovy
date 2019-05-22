package org.openkilda.functionaltests.spec.grpc

import org.openkilda.grpc.speaker.model.LogicalPortDto
import org.openkilda.messaging.error.MessageError

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
    def "Able to create/read/delete logicalport on the #switches.switchId switch"() {
        /**the update action is not working(issue on a Noviflow switch side)*/
        when: "Create logical port"
        def switchPort = northbound.getPorts(switches.switchId).find { it.state[0] == "LINK_DOWN" }.portNumber
        def switchLogicalPort = 1100 + switchPort
        def responseAfterCreating = grpc.createLogicalPort(switches.address, new LogicalPortDto([switchPort], switchLogicalPort))
        assert responseAfterCreating.logicalPortNumber.value == switchLogicalPort

        then: "Able to get the created logical port"
        def responseAfterGetting = grpc.getSwitchLogicalPortConfig(switches.address, switchLogicalPort)
        responseAfterGetting.logicalPortNumber == switchLogicalPort
        responseAfterGetting.name == "novi_lport" + switchLogicalPort.toString()
        responseAfterGetting.portNumbers[0] == switchPort

        and: "The created port is exist in a list of all logical port"
        grpc.getSwitchLogicalPorts(switches.address).contains(responseAfterGetting)

        //        TODO(andriidovhan): add update action
        //        and: "able to edit the created logical port"
        when: "Try to delete the created logical port"
        def responseAfterDeleting = grpc.deleteSwitchLogicalPort(switches.address, switchLogicalPort)

        then: "Logical port is deleted"
        responseAfterDeleting.deleted

        when: "Try to get the deleted logical port"
        grpc.getSwitchLogicalPortConfig(switches.address, switchLogicalPort)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage == "Provided logical port does not exist."
        Boolean testIsCompleted = true

        cleanup: "Remove created port"
        if (!testIsCompleted) {
            grpc.deleteSwitchLogicalPort(switches.address, switchLogicalPort)
        }

        where:
        switches << getNoviflowSwitches()
    }

    @Unroll
    def "Not able to create logical port with incorrect port number(lPort/sPort): \
#data.logicalPortNumber/#data.portNumber on the #sw.switchId switch"() {
        when:
        "Try to create logical port: #logicalPortNumber/#portNumber"
        def switchPort = northbound.getPorts(sw.switchId).find { it.state[0] == "LINK_DOWN" }.portNumber
        def switchLogicalPort = 1100 + switchPort
        def pNumber = data.portNumber ? data.portNumber : switchPort
        def lPortNumber = data.logicalPortNumber ? data.logicalPortNumber : switchLogicalPort
        grpc.createLogicalPort(sw.address, new LogicalPortDto([pNumber], lPortNumber))

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
                ], noviflowSwitches].combinations()
    }

    @Unroll
    def "Not able to delete non-existent logical port number on the #switches.switchId switch"() {
        when: "Try to delete incorrect logicalPortNumber"
        //        TODO(andriidovhan) add check that fakeNumber is not exist on a switch
        Integer fakeNumber = 11114
        grpc.deleteSwitchLogicalPort(switches.address, fakeNumber)

        then: "Human readable error is returned."
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage == "Provided logical port does not exist."

        where:
        switches << getNoviflowSwitches()
    }
}
