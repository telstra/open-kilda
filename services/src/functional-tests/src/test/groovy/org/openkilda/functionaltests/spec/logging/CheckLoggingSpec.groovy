package org.openkilda.functionaltests.spec.logging

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
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

    def "Check Northbound, Storm and Topology Engine logging"() {
        when: "A non-existent flow is requested"
        int timeout = 180
        def flowId = "nonexistentflowid" + System.currentTimeMillis()
        try {
            northbound.getFlow(flowId)
        } catch (HttpClientErrorException e) {

        }

        and: "Rules on a switch are validated"
        def switchId = topology.activeSwitches.first().dpId
        northbound.validateSwitchRules(switchId)

        then: "Northbound, Storm and Topology Engine should log these actions within 3 minutes"
        Wrappers.wait(timeout, 10) {
            def nbLogs = elastic.getLogs(new ElasticQueryBuilder().setTags(KildaTags.NORTHBOUND).
                    setTimeRange(timeout * 2).setLevel("ERROR").build())
            def stormLogs = elastic.getLogs(new ElasticQueryBuilder().setTags(KildaTags.STORM_WORKER).
                    setTimeRange(timeout * 2).setLevel("ERROR").build())
            def tpLogs = elastic.getLogs(new ElasticQueryBuilder().setTags(KildaTags.TOPOLOGY_ENGINE).
                    setTimeRange(timeout * 2).setLevel("INFO").build())

            assert nbLogs?.hits?.hits?.any { hit -> hit.source.message.contains(flowId) }:
                    "Northbound should generate an error message about not being able to find a flow"
            assert stormLogs?.hits?.hits?.any { hit -> hit.source.message.contains(flowId) }:
                    "Storm should generate an error message about not being able to find a flow"
            assert tpLogs?.hits?.hits?.any { hit -> hit.source.message.contains(switchId.toString()) }:
                    "Topology Engine should generate an info message in case of a switch rules validation event"
        }
    }
}
