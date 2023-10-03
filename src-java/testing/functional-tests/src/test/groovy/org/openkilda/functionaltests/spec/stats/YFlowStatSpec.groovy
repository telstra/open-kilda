package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.functionaltests.model.stats.YFlowStats
import org.openkilda.functionaltests.model.stats.YFlowStatsMetric
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

import static groovyx.gpars.GParsPool.withPool
import static groovyx.gpars.GParsPoolUtil.callAsync
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_EGRESS_BYTES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_EGRESS_BITS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_EGRESS_PACKETS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_INGRESS_BITS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_INGRESS_BYTES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_INGRESS_PACKETS
import static org.openkilda.functionaltests.model.stats.YFlowStatsMetric.Y_FLOW_SHARED_BITS

@Tags(LOW_PRIORITY)
@Narrative("Verify that statistic is collected for different type of Y-flow")
class YFlowStatSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Autowired
    @Shared
    YFlowHelper yFlowHelper
    @Shared
    int traffgenRunDuration = 5 //seconds
    @Shared
    YFlowStats stats
    @Shared
    FlowStats subflow1Stats
    @Shared
    FlowStats subflow2Stats
    @Shared
    def yFlow
    @Autowired @Shared
    YFlowStats yFlowStats
    @Autowired @Shared
    FlowStats flowStats

    def setupSpec() {
        def switchTriplet = topologyHelper.getSwitchTriplets(false, false).find {
            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens }
        } ?: assumeTrue(false, "No suiting switches found")
        yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(switchTriplet).tap {maximumBandwidth = 10})
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildYFlowExam(yFlow, yFlow.maximumBandwidth * 10, traffgenRunDuration)
        Wrappers.wait(statsRouterRequestInterval * 4) {
            withPool {
                [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    traffExam.waitExam(direction)
                }
            }
            statsHelper."force kilda to collect stats"()
            assert yFlowStats.of(yFlow.getYFlowId()).get(Y_FLOW_SHARED_BITS).getDataPoints().size() > 2
        }
        stats = yFlowStats.of(yFlow.getYFlowId())
        subflow1Stats = flowStats.of(yFlow.getSubFlows().get(0).getFlowId())
        subflow2Stats = flowStats.of(yFlow.getSubFlows().get(1).getFlowId())
    }

    @Tidy
    @Unroll
    def "System is able to collect #stat meter stats and they grow monotonically"() {
        when: "Stats were collected"
        then: "#stat stats is available"
        assert stats.get(stat).isGrowingMonotonically(),
                "Collected statistics doesn't show growing stats. Actual:${stat}"

        where:
        stat << YFlowStatsMetric.getEnumConstants()
    }

    @Tidy
    @Unroll
    def "System is able to collect subflow #stat-#direction stats and they grow monotonically"() {
        when: "Stats were collected"
        then: "#stat stats is available"
        assert subflow1Stats.get(stat, direction).isGrowingMonotonically(),
                "Collected statistics doesn't show growing stats. Actual:${subflow1Stats}"
        assert subflow2Stats.get(stat, direction).isGrowingMonotonically(),
                "Collected statistics doesn't show growing stats. Actual:${subflow2Stats}"


        where:
        //RTT stats are tested in Server42FlowRttSpec
        //Subflow meters aren't used, Y-FLow meters grab the stats themselves
        //Raw meters are valid for transit switches only
        [stat, direction] << [[FLOW_INGRESS_PACKETS,
                               FLOW_EGRESS_PACKETS,
                               FLOW_INGRESS_BYTES,
                               FLOW_EGRESS_BYTES,
                               FLOW_INGRESS_BITS,
                               FLOW_EGRESS_BITS],
                              [Direction.FORWARD, Direction.REVERSE]].combinations()
    }

    def cleanupSpec() {
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    def "workaround failure on first connect to kafka"() {
        withPool {
            callAsync
                    {
                        statsHelper."force kilda to collect stats"("fake")
                    }
        }
    }
}
