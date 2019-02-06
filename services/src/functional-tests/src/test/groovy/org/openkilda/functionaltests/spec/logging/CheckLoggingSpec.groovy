package org.openkilda.functionaltests.spec.logging

import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.testing.service.elastic.ElasticQueryBuilder
import org.openkilda.testing.service.elastic.ElasticService
import org.openkilda.testing.service.elastic.model.KildaTags

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Slf4j
@Narrative("This specification ensures that all logging facilities are up and running after Kilda deployment")
class CheckLoggingSpec extends BaseSpecification {

    @Autowired
    ElasticService elastic

    String discoveryMessage = "push discovery package via"

    def "Check Floodlight logging"() {
        when: "Retrieve floodlight logs for last 5 minutes"
        def result = elastic.getLogs(new ElasticQueryBuilder().setTags(KildaTags.FLOODLIGHT)
                .setLevel("INFO").setTimeRange(300).build())

        assert result?.hits?.total > 0: "No logs could be found for Floodlight"

        then: "There should be discovery messages"
        result.hits.hits.any { hit -> hit.source.message.toLowerCase().contains(discoveryMessage) }
    }

    def "Check Northbound and Storm logging"() {
        when: "A non-existent switch is requested"
        northbound.getSwitch(NON_EXISTENT_SWITCH_ID)

        then: "An error is received (404 code)"
        def switchExc = thrown(HttpClientErrorException)
        def switchErrorMsg = "Switch $NON_EXISTENT_SWITCH_ID not found"

        switchExc.rawStatusCode == 404
        switchExc.responseBodyAsString.to(MessageError).errorMessage.contains(switchErrorMsg)

        when: "A non-existent flow is requested"
        def flowId = "nonexistentFlowId" + System.currentTimeMillis()
        northbound.getFlow(flowId)

        then: "An error is received (404 code)"
        def flowExc = thrown(HttpClientErrorException)
        def flowErrorMsg = "Can not get flow: Flow $flowId not found"

        flowExc.rawStatusCode == 404
        flowExc.responseBodyAsString.to(MessageError).errorMessage.contains(flowErrorMsg)

        and: "Northbound and Storm should log these actions within 30 seconds"
        int timeout = 31
        Wrappers.wait(timeout, 10) {
            def nbLogs = elastic.getLogs(new ElasticQueryBuilder().setTags(KildaTags.NORTHBOUND).
                    setTimeRange(timeout * 2).setLevel("ERROR").build())
            def stormLogs = elastic.getLogs(new ElasticQueryBuilder().setTags(KildaTags.STORM_WORKER).
                    setTimeRange(timeout * 2).setLevel("WARN").build())

            assert nbLogs?.hits?.hits?.any { hit -> hit.source.message.contains(switchErrorMsg) }:
                    "Northbound should generate an error message about not being able to find the switch"
            assert stormLogs?.hits?.hits?.any { hit -> hit.source.message.contains(flowErrorMsg) }:
                    "Storm should generate an error message about not being able to find the flow"
        }
    }
}
