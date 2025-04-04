package org.openkilda.functionaltests.spec.grpc

import org.openkilda.functionaltests.error.LogServerNotSetExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.grpc.speaker.model.LogMessagesDto
import org.openkilda.grpc.speaker.model.LogOferrorsDto
import org.openkilda.grpc.speaker.model.RemoteLogServerDto
import org.openkilda.messaging.model.grpc.OnOffState
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.testing.ConstantsGrpc.DEFAULT_LOG_MESSAGES_STATE
import static org.openkilda.testing.ConstantsGrpc.REMOTE_LOG_IP
import static org.openkilda.testing.ConstantsGrpc.REMOTE_LOG_PORT

@Narrative("""This test suite checks that we are able to enable/disable:
 - log messages;
 - OF log messages.
 And checks that we are able to do the CRUD actions with the remote log server configuration.""")
class LogSpec extends GrpcBaseSpecification {
    @Value('${grpc.remote.log.server.ip}')
    String defaultRemoteLogServerIp
    @Value('${grpc.remote.log.server.port}')
    Integer defaultRemoteLogServerPort
    @Autowired @Shared
    CleanupManager cleanupManager

    def "Able to enable 'log messages' on the #sw.hardware-#sw.software switch"() {
        when: "Try to turn on 'log messages'"
        cleanupManager.addAction(OTHER,
                {grpc.enableLogMessagesOnSwitch(sw.address, new LogMessagesDto(DEFAULT_LOG_MESSAGES_STATE))})
        def responseAfterTurningOn = grpc.enableLogMessagesOnSwitch(sw.address, new LogMessagesDto(OnOffState.ON))

        then: "The 'log messages' is turned on"
        responseAfterTurningOn.enabled == OnOffState.ON

        when: "Try to turn off 'log messages'"
        def responseAfterTurningOff = grpc.enableLogMessagesOnSwitch(sw.address,
                new LogMessagesDto(OnOffState.OFF))

        then: "The 'log messages' is turned off"
        responseAfterTurningOff.enabled == OnOffState.OFF

        where:
        sw << getNoviflowSwitches()
    }

    def "Able to enable 'OF log messages' on #sw.hardware-#sw.software"() {
        when: "Try to turn on 'OF log messages'"
        cleanupManager.addAction(OTHER,
                {grpc.enableLogMessagesOnSwitch(sw.address, new LogMessagesDto(DEFAULT_LOG_MESSAGES_STATE))})
        def responseAfterTurningOn = grpc.enableLogOfErrorsOnSwitch(sw.address,
                new LogOferrorsDto(OnOffState.ON))

        then: "The 'OF log messages' is turned on"
        responseAfterTurningOn.enabled == OnOffState.ON

        when: "Try to turn off 'OF log messages'"
        def responseAfterTurningOff = grpc.enableLogOfErrorsOnSwitch(sw.address,
                new LogOferrorsDto(OnOffState.OFF))

        then: "The 'OF log messages' is turned off"
        responseAfterTurningOff.enabled == OnOffState.OFF

        where:
        sw << getNoviflowSwitches()
    }

    def "Able to manipulate(CRUD) with a remote log server on #sw.hardware-#sw.software"() {
        when: "Remove current remote log server configuration"
        cleanupManager.addAction(OTHER, {grpc.setRemoteLogServerForSwitch(sw.address,
                new RemoteLogServerDto(defaultRemoteLogServerIp, defaultRemoteLogServerPort))})
        def response = grpc.deleteRemoteLogServerForSwitch(sw.address)

        then: "Current remote log server configuration is deleted"
        response.deleted

        /* https://github.com/telstra/open-kilda/issues/5408
        def response1 = grpc.getRemoteLogServerForSwitch(sw.address)
        response1.ipAddress == ""
        response1.port == 0*/

        when: "Try to set custom remote log server"
        def response2 = grpc.setRemoteLogServerForSwitch(sw.address,
                new RemoteLogServerDto(REMOTE_LOG_IP, REMOTE_LOG_PORT))
        assert response2.ipAddress == REMOTE_LOG_IP
        assert response2.port == REMOTE_LOG_PORT

        then: "New custom remote log server configuration is set"
        def response3 = grpc.getRemoteLogServerForSwitch(sw.address)
        response3.ipAddress == REMOTE_LOG_IP
        response3.port == REMOTE_LOG_PORT

        where:
        sw << getNoviflowSwitches()
    }

    @Tags(HARDWARE)
    @Ignore("https://github.com/telstra/open-kilda/issues/3972")
    def "Not able to set incorrect remote log server configuration(ip/port): #data.remoteIp/#data.remotePort \
on #sw.hardware-#sw.software"() {
        when: "Try to set incorrect configuration"
        grpc.setRemoteLogServerForSwitch(sw.address, new RemoteLogServerDto(data.remoteIp, data.remotePort))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new LogServerNotSetExpectedError(data.errorMessage).matches(exc)

        where:
        [data, sw] << [
                [
                        [remoteIp    : "1.1.1.1111",
                         remotePort  : REMOTE_LOG_PORT,
                         errorMessage: "Invalid hostname,please provide valid hostname."],
                        [remoteIp    : REMOTE_LOG_IP,
                         remotePort  : 65537,
                         errorMessage: "Invalid remotelogserver port."]
                ], noviflowSwitches].combinations()
    }
}
