package org.openkilda.functionaltests.spec.northbound.links

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.northbound.dto.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException

class LinkSpec extends BaseSpecification {
    @Autowired
    NorthboundService northboundService
    @Autowired
    TopologyDefinition topology

    @Value('${discovery.interval}')
    int discoveryInterval

    def "Cannot delete nonexistent link"() {
        given: "Parameters of nonexistent link"
        def parameters = new LinkParametersDto("srcSwitch1", 100, "dstSwitch1", 100)

        when: "Try to delete nonexistent link"
        northboundService.deleteLink(parameters)

        then: "Got 404 NotFound"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.contains("Link is not found")
    }

    def "Cannot delete active link"() {
        given: "Parameters for active link"
        def link = northboundService.getActiveLinks()[0]
        def parameters = new LinkParametersDto(link.path[0].switchId.toString(), link.path[0].portNo,
                                               link.path[1].switchId.toString(), link.path[1].portNo)

        when: "Try to delete active link"
        northboundService.deleteLink(parameters)

        then: "Got 400 BadRequest because link is active"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.contains("ISL must not be in active state")
    }

    def "Can delete inactive link"() {
        given: "Parameters for inactive link"
        def link = northboundService.getActiveLinks()[0]
        def srcSwitch = link.path[0].switchId
        def srcPort = link.path[0].portNo
        def dstSwitch = link.path[1].switchId
        def dstPort = link.path[1].portNo
        northboundService.portDown(srcSwitch, srcPort)
        assert Wrappers.wait(WAIT_OFFSET) {
            northboundService.getLinksByParameters(srcSwitch, srcPort, dstSwitch, dstPort)
                    .every { it.state == IslChangeType.FAILED }
        }
        def parameters = new LinkParametersDto(srcSwitch.toString(), srcPort, dstSwitch.toString(), dstPort)

        when: "Try to delete inactive link"
        def res = northboundService.deleteLink(parameters)

        then: "Check that link is actually deleted"
        res.deleted
        Wrappers.wait(WAIT_OFFSET) {
            northboundService.getLinksByParameters(srcSwitch, srcPort, dstSwitch, dstPort).empty
        }

        and: "Cleanup: restore link"
        northboundService.portUp(srcSwitch, srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getLinksByParameters(srcSwitch, srcPort, dstSwitch, dstPort)
                    .every { it.state == IslChangeType.DISCOVERED }
        }
    }
}