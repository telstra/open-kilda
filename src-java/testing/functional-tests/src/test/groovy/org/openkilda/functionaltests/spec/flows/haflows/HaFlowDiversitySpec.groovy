package org.openkilda.functionaltests.spec.flows.haflows


import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.YFlowHelper

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify the ability to create diverse ha-flows in the system.")
class HaFlowDiversitySpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "Able to create diverse ha-flows"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three ha-flows with diversity enabled"
        def haFlowRequest1 = haFlowHelper.randomHaFlow(swT)
        def haFlow1 = haFlowHelper.addHaFlow(haFlowRequest1)
        def haFlowRequest2 = haFlowHelper.randomHaFlow(swT, [haFlowRequest1])
                .tap { it.diverseFlowId = haFlow1.haFlowId }
        def haFlow2 = haFlowHelper.addHaFlow(haFlowRequest2)
        def haFlowRequest3 = haFlowHelper.randomHaFlow(swT, [haFlowRequest1, haFlowRequest2])
                .tap { it.diverseFlowId = haFlow2.haFlowId }
        def haFlow3 = haFlowHelper.addHaFlow(haFlowRequest3)

        then: "HaFlow create response contains info about diverse haFlow"
        !haFlow1.diverseWithHaFlows
        haFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        haFlow3.diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()

        and: "All ha-flows have diverse ha-flow IDs in response"
        northboundV2.getHaFlow(haFlow1.haFlowId).diverseWithHaFlows.sort() == [haFlow2.haFlowId, haFlow3.haFlowId].sort()
        northboundV2.getHaFlow(haFlow2.haFlowId).diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow3.haFlowId].sort()
        northboundV2.getHaFlow(haFlow3.haFlowId).diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()

        and: "All ha-flows have different paths"
        //TODO add checks when https://github.com/telstra/open-kilda/issues/5148 will be implemented

        and: "Ha-flows histories contains 'diverse?' information"
        //TODO add checks when history records will be added

        and: "Ha-flow passes flow validation"
        //TODO add validation when https://github.com/telstra/open-kilda/issues/5153 will be implemented

        when: "Delete ha-flows"
        [haFlow1, haFlow2, haFlow3].each { it && haFlowHelper.deleteHaFlow(it.haFlowId) }
        def haFlowsAreDeleted = true

        then: "HaFlows' histories contain 'diverseGroupId' information in 'delete' operation"
        //TODO add checks when history records will be added

        cleanup:
        !haFlowsAreDeleted && [haFlow1, haFlow2, haFlow3].each { it && haFlowHelper.deleteHaFlow(it.haFlowId) }
    }

    @Tidy
    def "Able to create ha-flow diverse with common flow"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Switches with two not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    it.pathsEp1.collect { pathHelper.getInvolvedIsls(it) }
                            .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 2
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create an ha-flow without diversity"
        def haFlowRequest1 = haFlowHelper.randomHaFlow(swT)
        def haFlow1 = haFlowHelper.addHaFlow(haFlowRequest1)

        and: "Create a simple multiSwitch flow diverse with ha flow"
        def flowRequest = flowHelperV2.randomFlow(swT.shared, swT.ep1, false)
                .tap { diverseFlowId = haFlow1.getHaFlowId() }
        def flow = flowHelperV2.addFlow(flowRequest)

        and: "Create an ha-flow diverse with simple flow"
        def haFlowRequest2 = haFlowHelper.randomHaFlow(swT)
                .tap { it.diverseFlowId = flow.flowId }
        def haFlow2 = haFlowHelper.addHaFlow(haFlowRequest2)

        then: "Create response contains correct info about diverse flows"
        !haFlow1.diverseWithHaFlows
        !haFlow1.diverseWithFlows
        !haFlow1.diverseWithYFlows
        flow.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !flow.diverseWith
        !flow.diverseWithYFlows
        haFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        haFlow2.diverseWithFlows == [flow.flowId] as Set
        !haFlow2.diverseWithYFlows

        when: "Get flow and ha-flows"
        def getHaFlow1 = northboundV2.getHaFlow(haFlow1.haFlowId)
        def getHaFlow2 = northboundV2.getHaFlow(haFlow2.haFlowId)
        def getFlow = northboundV2.getFlow(flow.flowId)

        then: "All get flow responses have correct diverse flow IDs"
        getHaFlow1.diverseWithHaFlows == [haFlow2.haFlowId] as Set
        getHaFlow1.diverseWithFlows == [flow.flowId] as Set
        !getHaFlow1.diverseWithYFlows
        getHaFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        getHaFlow2.diverseWithFlows == [flow.flowId] as Set
        !getHaFlow2.diverseWithYFlows
        getFlow.diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()
        !getFlow.diverseWith
        !getFlow.diverseWithYFlows

        cleanup:
        haFlow1 && haFlowHelper.deleteHaFlow(haFlow1.haFlowId)
        haFlow2 && haFlowHelper.deleteHaFlow(haFlow2.haFlowId)
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Able to create ha-flow diverse with y-flow"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three ha-flows with diversity enabled"
        def haFlowRequest1 = haFlowHelper.randomHaFlow(swT)
        def haFlow1 = haFlowHelper.addHaFlow(haFlowRequest1)
        def yFlowRequest1 = yFlowHelper.randomYFlow(swT, false)
                .tap { it.diverseFlowId = haFlow1.haFlowId }
        def yFlow = yFlowHelper.addYFlow(yFlowRequest1)
        def haFlowRequest2 = haFlowHelper.randomHaFlow(swT, [haFlowRequest1])
                .tap { it.diverseFlowId = yFlow.getYFlowId() }
        def haFlow2 = haFlowHelper.addHaFlow(haFlowRequest2)

        then: "HaFlow create response contains info about diverse haFlow"
        !haFlow1.diverseWithHaFlows
        !haFlow1.diverseWithFlows
        !haFlow1.diverseWithYFlows
        yFlow.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !yFlow.diverseWithFlows
        !yFlow.diverseWithYFlows
        haFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !haFlow2.diverseWithFlows
        haFlow2.diverseWithYFlows == [yFlow.getYFlowId()] as Set

        when: "Get y-flow and ha-flows"
        def getHaFlow1 = northboundV2.getHaFlow(haFlow1.haFlowId)
        def getHaFlow2 = northboundV2.getHaFlow(haFlow2.haFlowId)
        def getYFlow = northboundV2.getYFlow(yFlow.getYFlowId())

        then: "All get flow responses have correct diverse flow IDs"
        getHaFlow1.diverseWithHaFlows == [haFlow2.haFlowId] as Set
        !getHaFlow1.diverseWithFlows
        getHaFlow1.diverseWithYFlows == [yFlow.getYFlowId()] as Set
        getHaFlow2.diverseWithHaFlows == [haFlow1.haFlowId] as Set
        !getHaFlow2.diverseWithFlows
        getHaFlow2.diverseWithYFlows == [yFlow.getYFlowId()] as Set
        getYFlow.diverseWithHaFlows.sort() == [haFlow1.haFlowId, haFlow2.haFlowId].sort()
        !getYFlow.diverseWithFlows
        !getYFlow.diverseWithYFlows

        cleanup:
        haFlow1 && haFlowHelper.deleteHaFlow(haFlow1.haFlowId)
        haFlow2 && haFlowHelper.deleteHaFlow(haFlow2.haFlowId)
        yFlow && yFlowHelper.deleteYFlow(yFlow.getYFlowId())
    }
}
