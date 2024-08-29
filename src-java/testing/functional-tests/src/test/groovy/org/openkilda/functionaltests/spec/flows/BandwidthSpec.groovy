package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.messaging.payload.flow.FlowState.UP

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithMissingPathExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedWithMissingPathExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify that ISL's bandwidth behaves consistently and does not allow any oversubscribtions etc.")

class BandwidthSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags(SMOKE)
    def "Available bandwidth on ISLs changes respectively when creating/updating/deleting a flow"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        when: "Create a flow with a valid bandwidth"
        def linksBeforeFlowCreate = northbound.getAllLinks()
        def maximumBandwidth = 1000

        def flow = flowFactory.getBuilder(switchPair).withBandwidth(maximumBandwidth).build().create()
        assert flow.maximumBandwidth == maximumBandwidth

        then: "Available bandwidth on ISLs is changed in accordance with flow maximum bandwidth"
        def linksAfterFlowCreate = northbound.getAllLinks()
        def involvedIsls = flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        checkBandwidth(involvedIsls, linksBeforeFlowCreate, linksAfterFlowCreate, -flow.maximumBandwidth)

        when: "Update the flow with a valid bandwidth"
        def maximumBandwidthUpdated = 2000
        def updatedFlow = flow.update(flow.tap { it.maximumBandwidth = maximumBandwidthUpdated })

        then: "The flow is successfully updated and has 'Up' status"
        updatedFlow.maximumBandwidth == maximumBandwidthUpdated

        and: "Available bandwidth on ISLs is changed in accordance with new flow maximum bandwidth"
        def linksBeforeFlowUpdate = linksAfterFlowCreate
        def linksAfterFlowUpdate = northbound.getAllLinks()
        def involvedIslsAfterUpdating = updatedFlow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        involvedIslsAfterUpdating.sort() == involvedIsls.sort()

        checkBandwidth(involvedIslsAfterUpdating, linksBeforeFlowUpdate, linksAfterFlowUpdate,
                maximumBandwidth - maximumBandwidthUpdated)

        when: "Delete the flow"
        updatedFlow.delete()

        then: "Available bandwidth on ISLs is changed to the initial value before flow creation"
        def linksAfterFlowDelete = northbound.getAllLinks()
        checkBandwidth(involvedIslsAfterUpdating, linksBeforeFlowCreate, linksAfterFlowDelete)
    }

    def "Longer path is chosen in case of not enough available bandwidth on a shorter path"() {
        given: "Two active switches with two possible flow paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def availablePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }

        // Make the first path more preferable than others.
        def preferablePathIsls = availablePaths[0]
        availablePaths[1..-1].each { islHelper.makePathIslsMorePreferable(preferablePathIsls, it) }

        // Get min available bandwidth on the preferable path.
        def involvedBandwidths = []
        def allLinks = northbound.getAllLinks()
        preferablePathIsls.each {
            involvedBandwidths.add(islUtils.getIslInfo(allLinks, it).get().availableBandwidth)
        }
        def minAvailableBandwidth = involvedBandwidths.min()

        when: "Create a flow to reduce available bandwidth on links of the expected preferable path"
        def flow1 = flowFactory.getBuilder(switchPair).withBandwidth(minAvailableBandwidth - 100).build().create()
        def flow1PathIsls = flow1.retrieveAllEntityPaths().flowPath.getInvolvedIsls()

        then: "The flow is really built through the expected preferable path"
        flow1PathIsls == preferablePathIsls

        when: "Create another flow. One path is shorter but available bandwidth is not enough, another path is longer"
        def flow2 = flowFactory.getBuilder(switchPair, false, flow1.occupiedEndpoints())
                .withBandwidth(101).build().create()

        then: "The flow is built through longer path where available bandwidth is enough"
        def flow2PathIsls = flow2.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        islHelper.getCost(flow2PathIsls) > islHelper.getCost(flow1PathIsls)
    }

    def "Unable to exceed bandwidth limit on ISL when creating a flow"() {
        given: "Two active switches"
        def switchPair = switchPairs.all().neighbouring().random()

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL"
        def involvedBandwidths = []
        switchPair.retrieveAvailablePaths().each { path ->
            path.getInvolvedIsls().each { link ->
                involvedBandwidths.add(islUtils.getIslInfo(link).get().availableBandwidth)
            }
        }
        def invalidFlowEntity = flowFactory.getBuilder(switchPair).withBandwidth(involvedBandwidths.max() + 1).build()
        invalidFlowEntity.create()

        then: "The flow is not created because flow path should not be found"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found.\
 Switch ${invalidFlowEntity.source.switchId} doesn't have links with enough bandwidth,\
 Failed to find path with requested bandwidth=${invalidFlowEntity.maximumBandwidth}/).matches(exc)
    }

    def "Unable to exceed bandwidth limit on ISL when updating a flow"() {
        given: "Two active switches"
        def switchPair = switchPairs.all().neighbouring().random()

        when: "Create a flow with a valid bandwidth"
        def maximumBandwidth = 1000

        def flow = flowFactory.getBuilder(switchPair).withBandwidth(maximumBandwidth).build().create()
        assert flow.maximumBandwidth == maximumBandwidth

        and: "Update the flow with a bandwidth that exceeds available bandwidth on ISL"
        List<Long> involvedBandwidths = []
        switchPair.retrieveAvailablePaths().each { path ->
            path.getInvolvedIsls().each { link ->
                involvedBandwidths.add(islUtils.getIslInfo(link).get().availableBandwidth)
            }
        }

        flow.update(flow.tap { it.maximumBandwidth = involvedBandwidths.max() + 1 })

        then: "The flow is not updated because flow path should not be found"
        def e = thrown(HttpClientErrorException)
        new FlowNotUpdatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found.\
 Switch ${flow.source.switchId} doesn't have links with enough bandwidth,\
 Failed to find path with requested bandwidth=${flow.maximumBandwidth}/).matches(e)
    }

    def "Able to exceed bandwidth limit on ISL when creating/updating a flow with ignore_bandwidth=true"() {
        given: "Two active switches"
        def switchPair = switchPairs.all().neighbouring().random()

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL (ignore_bandwidth=true)"
        def linksBeforeFlowCreate = northbound.getAllLinks()
        long maxBandwidth = northbound.getAllLinks()*.availableBandwidth.max()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(maxBandwidth + 1)
                .withIgnoreBandwidth(true).build()
                .create()
        /*This creates a 40G+ flow, which is invalid for Centecs (due to too high meter rate). Ignoring this issue,
        since we are focused on proper path computation and link bw change, not the meter requirements, thus not
        using flowHelper.addFlow in order not to validate successful rules installation in this case*/
        assert flow.maximumBandwidth == maxBandwidth + 1

        then: "Available bandwidth on ISLs is not changed in accordance with flow maximum bandwidth"
        def linksAfterFlowCreate = northbound.getAllLinks()
        def involvedIsls = flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        checkBandwidth(involvedIsls, linksBeforeFlowCreate, linksAfterFlowCreate)

        when: "Update the flow with a bandwidth that exceeds available bandwidth on ISL (ignore_bandwidth = true)"
        def updatedFlow = flow.update(flow.tap { it.maximumBandwidth = maxBandwidth + 2 })

        then: "The flow is successfully updated and has 'Up' status"
        updatedFlow.maximumBandwidth == maxBandwidth + 2

        and: "Available bandwidth on ISLs is not changed in accordance with new flow maximum bandwidth"
        def linksAfterFlowUpdate = northbound.getAllLinks()
        def involvedIslsAfterUpdating = updatedFlow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()

        involvedIslsAfterUpdating == involvedIsls
        checkBandwidth(involvedIslsAfterUpdating, linksBeforeFlowCreate, linksAfterFlowUpdate)
    }

    def "Able to update bandwidth to maximum link speed without using alternate links"() {
        given: "Two active neighboring switches"
        def switchPair = switchPairs.all().neighbouring().random()

        // We need to handle the case when there are parallel links between chosen switches. So we make all parallel
        // links except the first link not preferable to avoid flow reroute when updating the flow.
        List<List<Isl>> parallelPaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { it.size() == 1 }
        def preferablePathIsls = parallelPaths.first()
        if (parallelPaths.size() > 1) {
            parallelPaths[1..-1].each { islHelper.makePathIslsMorePreferable(preferablePathIsls, it) }
        }

        when: "Create a flow with a valid small bandwidth"
        def maximumBandwidth = 1000

        def flow = flowFactory.getBuilder(switchPair).withBandwidth(maximumBandwidth).build().create()
        assert flow.maximumBandwidth == maximumBandwidth

        then: "Only one link is involved in flow path"
        def initialFlowPath = flow.retrieveAllEntityPaths()
        def involvedIsls = initialFlowPath.flowPath.getInvolvedIsls()
        involvedIsls.size() == 1
        involvedIsls == preferablePathIsls

        when: "Update flow bandwidth to maximum link speed"
        def linkSpeed = islUtils.getIslInfo(involvedIsls.first()).get().speed
        def updatedFlow = flow.update(flow.tap { it.maximumBandwidth = linkSpeed })

        then: "The flow is successfully updated and has 'Up' status"
        updatedFlow.maximumBandwidth == linkSpeed

        and: "The same path is used by updated flow"
        updatedFlow.retrieveAllEntityPaths() == initialFlowPath
    }

    def "System doesn't allow to exceed bandwidth limit on ISL while updating a flow with ignore_bandwidth=false"() {
        given: "Two active switches"
        def switchPair = switchPairs.all().neighbouring().random()

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL (ignore_bandwidth=true)"
        def linksBeforeFlowCreate = northbound.getAllLinks()
        long maxBandwidth = northbound.getAllLinks()*.availableBandwidth.max()
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(maxBandwidth + 1)
                .withIgnoreBandwidth(true).build()
                .create()

        assert flow.maximumBandwidth == maxBandwidth + 1

        then: "Available bandwidth on ISLs is not changed in accordance with flow maximum bandwidth"
        def linksAfterFlowCreate = northbound.getAllLinks()
        def initialPath = flow.retrieveAllEntityPaths()
        checkBandwidth(initialPath.flowPath.getInvolvedIsls(), linksBeforeFlowCreate, linksAfterFlowCreate)

        when: "Update the flow (ignore_bandwidth = false)"
        flow.update(flow.tap { it.ignoreBandwidth = false })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found.\
 Switch ${flow.source.switchId} doesn't have links with enough bandwidth,\
 Failed to find path with requested bandwidth=${flow.maximumBandwidth}/).matches(exc)

        and: "The flow is not updated and has 'Up' status"
        flow.waitForBeingInState(UP)
        flow.retrieveDetails().ignoreBandwidth

        and: "Available bandwidth on ISLs is not changed"
        def linksAfterFlowUpdate = northbound.getAllLinks()
        def flowPathAfterUpdate = flow.retrieveAllEntityPaths()
        flowPathAfterUpdate == initialPath
        checkBandwidth(flowPathAfterUpdate.flowPath.getInvolvedIsls(), linksBeforeFlowCreate, linksAfterFlowUpdate)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to exceed bandwidth limit on ISL when creating a flow [v1 api]"() {
        given: "Two active switches"
        def switchPair = switchPairs.all().neighbouring().random()

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL"
        def involvedBandwidths = []
        switchPair.retrieveAvailablePaths().each { path ->
           path.getInvolvedIsls().each { link ->
                involvedBandwidths.add(islUtils.getIslInfo(link).get().availableBandwidth)
            }
        }
        def invalidFlowEntity = flowFactory.getBuilder(switchPair).withBandwidth(involvedBandwidths.max() + 1).build()
        invalidFlowEntity.createV1()

        then: "The flow is not created because flow path should not be found"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found.\
 Switch ${invalidFlowEntity.source.switchId} doesn't have links with enough bandwidth,\
 Failed to find path with requested bandwidth=${invalidFlowEntity.maximumBandwidth}/).matches(exc)
    }

    private def checkBandwidth(List<Isl> involvedIsls, List<IslInfoData> linksBefore, List<IslInfoData> linksAfter,
                               long offset = 0) {
        involvedIsls.each { link ->
            [link, link.reversed].each {
                def bwBefore = islUtils.getIslInfo(linksBefore, it).get().availableBandwidth
                def bwAfter = islUtils.getIslInfo(linksAfter, it).get().availableBandwidth
                assert bwAfter == bwBefore + offset
            }
        }
    }
}
