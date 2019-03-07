package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.testing.ConstantsGrpc.DEFAULT_LOG_MESSAGES_STATE
import static org.openkilda.testing.ConstantsGrpc.DEFAULT_LOG_OF_MESSAGES_STATE
import static org.openkilda.testing.ConstantsGrpc.REMOTE_LOG_IP
import static org.openkilda.testing.ConstantsGrpc.REMOTE_LOG_PORT

import org.openkilda.functionaltests.BaseSpecification
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
import spock.lang.Shared
import spock.lang.Unroll

@Slf4j
@Narrative("""This test suite checks that we are able to enable/disable:
 - log messages;
 - OF log messages.
 And checks that we are able to do the CRUD actions with the remote log server configuration.""")
class LogSpec extends BaseSpecification {
    @Value('${grpc.remote.log.server.ip}')
    String defaultRemoteLogServerIp
    @Value('${grpc.remote.log.server.port}')
    Integer defaultRemoteLogServerPort

    @Shared
    String switchIp

    def setupOnce() {
        requireProfiles("hardware")
        def nFlowSwitch = northbound.activeSwitches.find { it.description =~ /NW[0-9]+.[0-9].[0-9]/ }
        def pattern = /(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\-){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/
        switchIp = (nFlowSwitch.address =~ pattern)[0].replaceAll("-", ".")
    }

    def "Able to enable log messages"() {
        when: "Try to turn on log messages"
        def r1 = grpc.enableLogMessagesOnSwitch(switchIp, new LogMessagesDto(OnOffState.ON))

        then: "The log messages is turned on"
        r1.enabled == OnOffState.ON

        when: "Try to turn off log messages"
        def r2 = grpc.enableLogMessagesOnSwitch(switchIp, new LogMessagesDto(OnOffState.OFF))

        then: "The log messages is turned off"
        r2.enabled == OnOffState.OFF

        cleanup: "Restore default state"
        grpc.enableLogMessagesOnSwitch(switchIp, new LogMessagesDto(DEFAULT_LOG_MESSAGES_STATE))
    }

    def "Able to enable OF log messages"() {
        when: "Try to turn on OF log messages"
        def r1 = grpc.enableLogOfErrorsOnSwitch(switchIp, new LogOferrorsDto(OnOffState.ON))

        then: "The OF log messages is turned on"
        r1.enabled == OnOffState.ON

        when: "Try to turn off log messages"
        def r2 = grpc.enableLogOfErrorsOnSwitch(switchIp, new LogOferrorsDto(OnOffState.OFF))

        then: "The OF log messages is turned off"
        assert r2.enabled == OnOffState.OFF

        cleanup: "Restore default state"
        grpc.enableLogOfErrorsOnSwitch(switchIp, new LogOferrorsDto(DEFAULT_LOG_OF_MESSAGES_STATE))
    }

    def "Able to manipulate(CRUD) with a remote log server"() {
        when: "Remove current remote log server configuration"
        def r = grpc.deleteRemoteLogServerForSwitch(switchIp)

        then: "Current remote log server configuration is deleted"
        r.deleted

        def r1 = grpc.getRemoteLogServerForSwitch(switchIp)
        r1.ipAddress == ""
        r1.port == 0

        when: "Try to set custom remote log server"
        def r2 = grpc.setRemoteLogServerForSwitch(switchIp, new RemoteLogServerDto(REMOTE_LOG_IP, REMOTE_LOG_PORT))
        r2.ipAddress == REMOTE_LOG_IP
        r2.port == REMOTE_LOG_PORT

        then: "New custom remote log server configuration is set"
        def r3 = grpc.getRemoteLogServerForSwitch(switchIp)
        r3.ipAddress == REMOTE_LOG_IP
        r3.port == REMOTE_LOG_PORT

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
