package org.openkilda.functionaltests.spec.xresilience

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId

import groovyx.gpars.group.DefaultPGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""This spec is aimed to test different race conditions and system behavior in a concurrent
 environment (using v2 APIs)""")
class ContentionSpec extends BaseSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    def "Parallel flow creation requests with the same name creates only 1 flow"() {
        when: "Create the same flow in parallel multiple times"
        def flowsAmount = 20
        def group = new DefaultPGroup(flowsAmount)

        def flow = flowFactory.getBuilder(switchPairs.all().nonNeighbouring().random()).build()
        def tasks = (1..flowsAmount).collect {
            group.task { flow.create() }
        }
        tasks*.join()

        then: "One flow is created"
        def okTasks = tasks.findAll { !it.isError() }
        okTasks.size() == 1
        okTasks[0].get().flowId == flow.flowId

        and: "Other requests have received a decline"
        def errors = tasks.findAll { it.isError() }.collect { it.getError() as HttpClientErrorException }
        with(errors) {
            size() == flowsAmount - 1
            it.each { assert it.statusCode == HttpStatus.CONFLICT }
        }
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/3934")
    def "Parallel flow crud requests properly allocate/deallocate bandwidth resources"() {
        when: "Create multiple flows on the same ISLs concurrently"
        def flowsAmount = 20
        def group = new DefaultPGroup(flowsAmount)
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        flowsAmount.times {
            def flowEntity = flowFactory.getBuilder(switchPairs.all().nonNeighbouring().random(), false, busyEndpoints).build()
            busyEndpoints.addAll(flowEntity.occupiedEndpoints())
            flows << flowEntity
        }

        def createTasks = flows.collect { flow ->
            group.task { flow.create()}
        }
        createTasks*.join()
        assert createTasks.findAll { it.isError() }.empty
        def relatedIsls = flows[0].retrieveAllEntityPaths().getInvolvedIsls()
        //all flows use same isls
        flows[1..-1].each { assert it.retrieveAllEntityPaths().getInvolvedIsls() == relatedIsls }

        then: "Available bandwidth on related isls is reduced based on bandwidth of created flows"
        relatedIsls.each { isl ->
            Wrappers.wait(WAIT_OFFSET) {
                with(northbound.getLink(isl)) {
                    availableBandwidth == maxBandwidth - flows.sum { it.maximumBandwidth }
                }
            }
        }

        when: "Simultaneously remove all the flows"
        def deleteTasks = flows.collect { flow ->
            group.task { flow.delete() }
        }
        deleteTasks*.get()

        then: "Available bandwidth on all related isls is reverted back to normal"
        Wrappers.wait(3) {
            relatedIsls.each {
                verifyAll(northbound.getLink(it)) {
                    availableBandwidth == maxBandwidth
                    maxBandwidth == speed
                }
            }
        }
    }

    def "Reroute can be simultaneously performed with sync rules requests, removeExcess=#removeExcess"() {
        given: "A flow with reroute potential"
        def swPair = switchPairs.all().nonNeighbouring().random()
        def availablePaths = swPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
        def flow = flowFactory.getRandom(swPair)

        def flowPathInfo = flow.retrieveAllEntityPaths()
        def mainPathIsls = isls.all().findInPath(flowPathInfo)
        def newPathIsls = availablePaths.find { it != mainPathIsls }
        availablePaths.findAll { it != newPathIsls }.each { isls.all().makePathIslsMorePreferable(newPathIsls, it) }

        List<SwitchExtended> relatedSwitches = switches.all()
                .findSpecific((mainPathIsls + newPathIsls).involvedSwIds.flatten() as List<SwitchId>)

        when: "Flow reroute is simultaneously requested together with sync rules requests for all related switches"
        withPool {
            def rerouteTask = { flow.reroute() }
            rerouteTask.callAsync()
            3.times { relatedSwitches.eachParallel { it.synchronize(removeExcess) } }
        }

        then: "Flow is Up and path has changed"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert isls.all().findInPath(flow.retrieveAllEntityPaths()) == newPathIsls
        }

        and: "Related switches have no rule discrepancies"
        Wrappers.wait(WAIT_OFFSET) {
            assert validateAndCollectFoundDiscrepancies(relatedSwitches).isEmpty()
        }

        and: "Flow is healthy"
        flow.validateAndCollectDiscrepancies().isEmpty()

        where: removeExcess << [
                false,
//                true https://github.com/telstra/open-kilda/issues/4214
        ]
    }
}
