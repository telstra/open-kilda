package org.openkilda.functionaltests.spec.resilience

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowState

import groovyx.gpars.group.DefaultPGroup
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative

@Narrative("""This spec is aimed to test different race conditions and system behavior in a concurrent
 environment.""")
class ContentionSpec extends BaseSpecification {

    def "Parallel flow creation requests with the same name creates only 1 flow"() {
        when: "Create the same flow in parallel multiple times"
        def flowsAmount = 15
        def group = new DefaultPGroup(flowsAmount)
        def flow = flowHelper.randomFlow(topologyHelper.notNeighboringSwitchPair)
        def tasks = (1..flowsAmount).collect {
            group.task { flowHelper.addFlow(flow) }
        }
        tasks*.join()

        then: "One flow is created"
        def okTasks = tasks.findAll { !it.isError() }
        okTasks.size() == 1
        okTasks[0].get().id == flow.id

        and: "Other requests have received a decline"
        def errors = tasks.findAll { it.isError() }.collect { it.getError() as HttpClientErrorException }
        with(errors) {
            size() == flowsAmount - 1
            it.each { assert it.statusCode == HttpStatus.CONFLICT }
        }

        cleanup: "Remove flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/2983")
    def "Parallel flow crud requests properly allocate/deallocate bandwidth resources"() {
        when: "Create multiple flows on the same ISLs concurrently"
        def flowsAmount = 15
        def group = new DefaultPGroup(flowsAmount)
        List<FlowCreatePayload> flows = []
        flowsAmount.times { flows << flowHelper.randomFlow(topologyHelper.notNeighboringSwitchPair, false, flows) }
        def createTasks = flows.collect { flow ->
            group.task { flowHelper.addFlow(flow) }
        }
        createTasks*.join()
        assert createTasks.findAll { it.isError() }.empty
        def relatedIsls = pathHelper.getInvolvedIsls(northbound.getFlowPath(flows[0].id))
        //all flows use same isls
        flows[1..-1].each { assert pathHelper.getInvolvedIsls(northbound.getFlowPath(it.id)) == relatedIsls }

        then: "Available bandwidth on related isls is reduced based on bandwidth of created flows"
        relatedIsls.each {
            with(northbound.getLink(it)) {
                availableBandwidth == maxBandwidth - flows.sum { it.maximumBandwidth }
            }
        }

        when: "Simultaneously remove all the flows"
        def deleteTasks = flows.collect { flow ->
            group.task { flowHelper.deleteFlow(flow.id) }
        }
        deleteTasks*.join()

        then: "Available bandwidth on all related isls is reverted back to normal"
        relatedIsls.each {
            verifyAll(northbound.getLink(it)) {
                availableBandwidth == maxBandwidth
                maxBandwidth == speed
            }
        }
    }
}
