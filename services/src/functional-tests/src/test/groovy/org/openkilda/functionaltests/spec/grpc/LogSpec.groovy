package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.testing.ConstantsGrpc.DEFAULT_LOG_MESSAGES_STATE
import static org.openkilda.testing.ConstantsGrpc.DEFAULT_LOG_OF_MESSAGES_STATE
import static org.openkilda.testing.ConstantsGrpc.REMOTE_LOG_IP
import static org.openkilda.testing.ConstantsGrpc.REMOTE_LOG_PORT

import org.openkilda.grpc.speaker.model.LogMessagesDto
import org.openkilda.grpc.speaker.model.LogOferrorsDto
import org.openkilda.grpc.speaker.model.RemoteLogServerDto
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.grpc.OnOffState

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""This test suite checks that we are able to enable/disable:
 - log messages;
 - OF log messages.
 And checks that we are able to do the CRUD actions with the remote log server configuration.""")
class LogSpec extends GrpcBaseSpecification {
    @Value('${grpc.remote.log.server.ip}')
    String defaultRemoteLogServerIp
    @Value('${grpc.remote.log.server.port}')
    Integer defaultRemoteLogServerPort

    @Unroll
    def "Able to enable 'log messages' on the #sw.switchId switch"() {
        when: "Try to turn on 'log messages'"
        def responseAfterTurningOn = grpc.enableLogMessagesOnSwitch(sw.address, new LogMessagesDto(OnOffState.ON))

        then: "The 'log messages' is turned on"
        responseAfterTurningOn.enabled == OnOffState.ON

        when: "Try to turn off 'log messages'"
        def responseAfterTurningOff = grpc.enableLogMessagesOnSwitch(sw.address,
                new LogMessagesDto(OnOffState.OFF))

        then: "The 'log messages' is turned off"
        responseAfterTurningOff.enabled == OnOffState.OFF

        cleanup: "Restore default state"
        grpc.enableLogMessagesOnSwitch(sw.address, new LogMessagesDto(DEFAULT_LOG_MESSAGES_STATE))

        where:
        sw << getNoviflowSwitches(6.4)
    }

    @Unroll
    def "Able to enable 'OF log messages'  on the #sw.switchId switch"() {
        when: "Try to turn on 'OF log messages'"
        def responseAfterTurningOn = grpc.enableLogOfErrorsOnSwitch(sw.address, new LogOferrorsDto(OnOffState.ON))

        then: "The 'OF log messages' is turned on"
        responseAfterTurningOn.enabled == OnOffState.ON

        when: "Try to turn off 'OF log messages'"
        def responseAfterTurningOff = grpc.enableLogOfErrorsOnSwitch(sw.address, new LogOferrorsDto(OnOffState.OFF))

        then: "The 'OF log messages' is turned off"
        responseAfterTurningOff.enabled == OnOffState.OFF

        cleanup: "Restore default state"
        grpc.enableLogOfErrorsOnSwitch(sw.address, new LogOferrorsDto(DEFAULT_LOG_OF_MESSAGES_STATE))

        where:
        sw << getNoviflowSwitches(6.4)
    }

    @Unroll
    def "Able to manipulate(CRUD) with a remote log server on the #sw.switchId switch"() {
        when: "Remove current remote log server configuration"
        def response = grpc.deleteRemoteLogServerForSwitch(sw.address)

        then: "Current remote log server configuration is deleted"
        response.deleted

        def response1 = grpc.getRemoteLogServerForSwitch(sw.address)
        response1.ipAddress == ""
        response1.port == 0

        when: "Try to set custom remote log server"
        def response2 = grpc.setRemoteLogServerForSwitch(sw.address,
                new RemoteLogServerDto(REMOTE_LOG_IP, REMOTE_LOG_PORT))
        assert response2.ipAddress == REMOTE_LOG_IP
        assert response2.port == REMOTE_LOG_PORT

        then: "New custom remote log server configuration is set"
        def response3 = grpc.getRemoteLogServerForSwitch(sw.address)
        response3.ipAddress == REMOTE_LOG_IP
        response3.port == REMOTE_LOG_PORT

        cleanup: "Restore original configuration"
        grpc.setRemoteLogServerForSwitch(sw.address,
                new RemoteLogServerDto(defaultRemoteLogServerIp, defaultRemoteLogServerPort))

        where:
        sw << getNoviflowSwitches(6.4)
    }

    @Unroll
    def "Not able to set incorrect remote log server configuration(ip/port): #data.remoteIp/#data.remotePort \
on the #sw.switchId switch"() {
        when: "Try to set incorrect configuration"
        grpc.setRemoteLogServerForSwitch(sw.address, new RemoteLogServerDto(data.remoteIp, data.remotePort))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == data.errorMessage

        where:
        [data, sw] << [
                [
                        [remoteIp    : "1.1.1.1111",
                         remotePort  : REMOTE_LOG_PORT,
                         errorMessage: "Invalid IPv4 address."],
                        [remoteIp    : REMOTE_LOG_IP,
                         remotePort  : 65537,
                         errorMessage: "Invalid remotelogserver port."]
                ], getNoviflowSwitches(6.4)].combinations()
    }
}
