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

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

@Slf4j
@Narrative("""This test suite checks that we are able to enable/disable:
 - log messages;
 - OF log messages.
 And checks that we are able to do the CRUD actions with the remote log server configuration.""")
class LogSpec extends GrpcBaseSpecification {
    @Value('${grpc.remote.log.server.ip}')
    String defaultRemoteLogServerIp
    @Value('${grpc.remote.log.server.port}')
    Integer defaultRemoteLogServerPort

    def "Able to enable 'log messages'"() {
        when: "Try to turn on 'log messages'"
        def responseAfterTurningOn = grpc.enableLogMessagesOnSwitch(switchIp, new LogMessagesDto(OnOffState.ON))

        then: "The 'log messages' is turned on"
        responseAfterTurningOn.enabled == OnOffState.ON

        when: "Try to turn off 'log messages'"
        def responseAfterTurningOff = grpc.enableLogMessagesOnSwitch(switchIp, new LogMessagesDto(OnOffState.OFF))

        then: "The 'log messages' is turned off"
        responseAfterTurningOff.enabled == OnOffState.OFF

        cleanup: "Restore default state"
        grpc.enableLogMessagesOnSwitch(switchIp, new LogMessagesDto(DEFAULT_LOG_MESSAGES_STATE))
    }

    def "Able to enable 'OF log messages'"() {
        when: "Try to turn on 'OF log messages'"
        def responseAfterTurningOn = grpc.enableLogOfErrorsOnSwitch(switchIp, new LogOferrorsDto(OnOffState.ON))

        then: "The 'OF log messages' is turned on"
        responseAfterTurningOn.enabled == OnOffState.ON

        when: "Try to turn off 'OF log messages'"
        def responseAfterTurningOff = grpc.enableLogOfErrorsOnSwitch(switchIp, new LogOferrorsDto(OnOffState.OFF))

        then: "The 'OF log messages' is turned off"
        responseAfterTurningOff.enabled == OnOffState.OFF

        cleanup: "Restore default state"
        grpc.enableLogOfErrorsOnSwitch(switchIp, new LogOferrorsDto(DEFAULT_LOG_OF_MESSAGES_STATE))
    }

    def "Able to manipulate(CRUD) with a remote log server"() {
        when: "Remove current remote log server configuration"
        def response = grpc.deleteRemoteLogServerForSwitch(switchIp)

        then: "Current remote log server configuration is deleted"
        response.deleted

        def response1 = grpc.getRemoteLogServerForSwitch(switchIp)
        response1.ipAddress == ""
        response1.port == 0

        when: "Try to set custom remote log server"
        def response2 = grpc.setRemoteLogServerForSwitch(switchIp, new RemoteLogServerDto(REMOTE_LOG_IP, REMOTE_LOG_PORT))
        assert response2.ipAddress == REMOTE_LOG_IP
        assert response2.port == REMOTE_LOG_PORT

        then: "New custom remote log server configuration is set"
        def response3 = grpc.getRemoteLogServerForSwitch(switchIp)
        response3.ipAddress == REMOTE_LOG_IP
        response3.port == REMOTE_LOG_PORT

        cleanup: "Restore original configuration"
        grpc.setRemoteLogServerForSwitch(switchIp,
                new RemoteLogServerDto(defaultRemoteLogServerIp, defaultRemoteLogServerPort))
    }

    @Unroll
    def "Not able to set incorrect remote log server configuration(ip/port): #remoteIp/#remotePort"() {
        when: "Try to set incorrect configuration"
        grpc.setRemoteLogServerForSwitch(switchIp, new RemoteLogServerDto(remoteIp, remotePort))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == errorMessage

        where:
        remoteIp      | remotePort      | errorMessage
        "1.1.1.1111"  | REMOTE_LOG_PORT | "Invalid IPv4 address."
        REMOTE_LOG_IP | 65537           | "Invalid remotelogserver port."
    }
}
