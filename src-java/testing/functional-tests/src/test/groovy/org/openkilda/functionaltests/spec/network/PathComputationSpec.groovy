package org.openkilda.functionaltests.spec.network

import static org.openkilda.functionaltests.helpers.model.PathComputationStrategy.*

import org.openkilda.functionaltests.helpers.factory.FlowFactory

import org.junit.jupiter.api.parallel.ResourceLock
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.ResourceLockConstants
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

@ResourceLock(ResourceLockConstants.DEFAULT_PATH_COMPUTATION)

class PathComputationSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    def "Default path computation strategy is used when flow does not specify it"() {
        given: "Default path computation strategy is COST"
        kildaConfiguration.updatePathComputationStrategy(COST.toString())

        and: "Switch pair with two paths at least"
        def swPair = switchPairs.all().withAtLeastNPaths(2).random()
        def availablePaths = swPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }

        and: "Update paths so that one path has minimal total latency and the other has minimal total cost"
        def costEffectivePath = availablePaths[0]
        def latencyEffectivePath = availablePaths[1]
        availablePaths.findAll { it != costEffectivePath }.each { islHelper.makePathIslsMorePreferable(costEffectivePath, it) }
        def latencyIsls = latencyEffectivePath.collectMany { [it, it.reversed] }
        latencyIsls.each { islHelper.updateIslLatency(it, 1) }

        when: "Create flow without selecting path strategy"
        def flowCreateResponse = flowFactory.getBuilder(swPair)
                .withPathComputationStrategy(null).build().sendCreateRequest()
        def flow = flowCreateResponse.waitForBeingInState(FlowState.UP)

        then: "Flow is created with 'Cost' strategy (current default)"
        flowCreateResponse.pathComputationStrategy == COST
        flow.pathComputationStrategy == COST

        and: "Flow is actually built on the path with the least cost"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == costEffectivePath

        when: "Update default strategy to LATENCY"
        kildaConfiguration.updatePathComputationStrategy(LATENCY.toString())

        then: "Existing flow remains with COST strategy and on the same path"
        flow.retrieveDetails().pathComputationStrategy == COST
        flow.retrieveAllEntityPaths().getInvolvedIsls() == costEffectivePath

        and: "Manual reroute of the flow responds that flow is already on the best path"
        !flow.reroute().rerouted

        when: "Create a new flow without specifying path computation strategy"
        def flow2 = flowFactory.getBuilder(swPair).withPathComputationStrategy(null).build()
        //re-set latencies in DB one more time in case they were recalculated automatically to higher values
        latencyIsls.each { database.updateIslLatency(it, 1) }
        def createResponse2 = flow2.sendCreateRequest()
        flow2 = flow2.waitForBeingInState(FlowState.UP)

        then: "New flow is created with 'Latency' strategy (current default)"
        createResponse2.pathComputationStrategy == LATENCY
        flow2.pathComputationStrategy == LATENCY

        and: "New flow actually uses path with the least latency (ignoring cost)"
        flow2.retrieveAllEntityPaths().getInvolvedIsls() == latencyEffectivePath
    }

    def "Flow path computation strategy can be updated from LATENCY to COST"() {
        given: "Switch pair with two paths at least"
        def swPair = switchPairs.all().withAtLeastNPaths(2).random()
        def availablePaths = swPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }

        and: "Update paths so that one path has minimal total latency and the other has minimal total cost"
        def costEffectivePath = availablePaths[0]
        def latencyEffectivePath = availablePaths[1]
        availablePaths.findAll { it != costEffectivePath }.each { islHelper.makePathIslsMorePreferable(costEffectivePath, it) }
        def latencyIsls = latencyEffectivePath.collectMany { [it, it.reversed] }
        latencyIsls.each { islHelper.updateIslLatency(it, 1) }

        when: "Create flow using Latency strategy"
        def flow = flowFactory.getBuilder(swPair)
                .withPathComputationStrategy(LATENCY).build()
                .create()

        then: "Flow is built on the least-latency path"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == latencyEffectivePath

        when: "Update flow path strategy to 'Cost'"
        flow.update(flow.tap{ it.pathComputationStrategy = COST })

        then: "Flow path has changed to the least-cost path"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == costEffectivePath
    }

    def "Target flow path computation strategy is not applied immediately in case flow was updated partially"() {
        given: "Switch pair with two paths at least"
        def swPair = switchPairs.all().withAtLeastNPaths(2).random()

        and: "A flow with cost strategy"
        def flow = flowFactory.getBuilder(swPair)
                .withPathComputationStrategy(COST).build()
                .create()

        when: "Update path computation strategy(cost -> latency) via partialUpdate"
        flow.partialUpdateV1(new FlowPatchDto().tap{ it.targetPathComputationStrategy = LATENCY.toString()})

        then: "Path computation strategy is not changed"
        with(flow.retrieveDetailsV1()) {
            pathComputationStrategy ==  COST
            targetPathComputationStrategy == LATENCY.toString().toLowerCase()
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Sync the flow"
        flow.sync()
        Wrappers.wait(WAIT_OFFSET / 2) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        then: "Path computation strategy is updated and targetPathComputationStrategy is deleted"
        with(flow.retrieveDetails()) {
            pathComputationStrategy == LATENCY
            !targetPathComputationStrategy
        }
    }

    def "Target path computation strategy is applied after updating/rerouting a flow"() {
        given: "Switch pair with two paths at least"
        def swPair = switchPairs.all().withAtLeastNPaths(2).random()

        and: "A flow with cost strategy"
        def flow = flowFactory.getBuilder(swPair)
                .withPathComputationStrategy(COST).build()
                .create()

        when: "Update path computation strategy(cost -> latency) via partialUpdate"
        flow.partialUpdateV1(new FlowPatchDto().tap {
            it.targetPathComputationStrategy = LATENCY.toString()
        })

        and: "Reroute the flow"
        flow.reroute()
        Wrappers.wait(WAIT_OFFSET / 2) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        then: "Path computation strategy is updated and targetPathComputationStrategy is deleted"
        with(flow.retrieveDetails()) {
            pathComputationStrategy == LATENCY
            !targetPathComputationStrategy
        }

        when: "Update path computation strategy(latency -> cost) via partialUpdate"
        flow.partialUpdateV1(new FlowPatchDto().tap {
            it.targetPathComputationStrategy = COST.toString()
        })

        and: "Update the flow"
        flow.update(flow.tap { it.pathComputationStrategy = null })

        then: "Path computation strategy is updated and targetPathComputationStrategy is deleted"
        with(flow.retrieveDetails()) {
            pathComputationStrategy == COST
            !targetPathComputationStrategy
        }
    }
}
