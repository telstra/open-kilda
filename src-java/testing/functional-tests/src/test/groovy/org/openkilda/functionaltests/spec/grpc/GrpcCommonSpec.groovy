package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE

import org.openkilda.functionaltests.extension.tags.Tags

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Ignore

class GrpcCommonSpec extends GrpcBaseSpecification {

    def "Able to get switch status on the #sw.hardware-#sw.software switch"() {
        when: "Get switch status"
        def response = grpc.getSwitchStatus(sw.address)

        then: "Response is not null and needed fields are returned"
        response.serialNumber
        response.uptime
        response.kernel
        response.memUsage != null
        response.ssdUsage != null
        response.ethLinks
        response.builds
        response.cpuPercentage != null

        where:
        sw << getNoviflowSwitches()
    }

    def "Able to get switch packet in out stats on the #sw.hardware-#sw.software (#sw.description) switch"() {
        when: "Get switch packet in out stats"
        def response = grpc.getPacketInOutStats(sw.address)

        then: "Response is not null and needed fields are returned"
        with(response) {
            [packetInTotalPackets, packetInTotalPacketsDataplane, packetInNoMatchPackets, packetInApplyActionPackets,
            packetInInvalidTtlPackets, packetInActionSetPackets, packetInGroupPackets, packetInPacketOutPackets,
            packetOutTotalPacketsHost, packetOutTotalPacketsDataplane, packetOutEth0InterfaceUp, replyStatus].every {
                it != null
            }
        }

        where:
        sw << getNoviflowSwitches()
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/3901")
    @Tags(HARDWARE)
    def "Not able to get switch status from a non-existent switch address"() {
        when: "Get switch status"
        def nonExistentSwAddress = "1.1.1.1"
        grpc.getSwitchStatus(nonExistentSwAddress)

        then: "Human readable error is returned"
        def exc = thrown(HttpServerErrorException)
        exc.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
    }
}
