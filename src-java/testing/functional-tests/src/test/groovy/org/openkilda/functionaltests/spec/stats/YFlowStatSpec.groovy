package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.model.stats.YFlowStats
import org.openkilda.testing.service.otsdb.model.StatsResult
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

@Tags(LOW_PRIORITY)
@Narrative("Verify that statistic is collected for different type of Y-flow")
class YFlowStatSpec extends BaseSpecification {
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
    def yFlow

    def setupSpec() {
        "workaround failure on first connect to kafka"()
        def switchTriplet = topologyHelper.getSwitchTriplets(false, false).find {
            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens }
        } ?: assumeTrue(false, "No suiting switches found")
        yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(switchTriplet))
        def beforeTraffic = new Date()
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildYFlowExam(yFlow, yFlow.maximumBandwidth, traffgenRunDuration)
        Wrappers.safeRunSeveralTimes(4, 0) {
            withPool {
                [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    traffExam.waitExam(direction)
                }
            }
            statsHelper."force kilda to collect stats"()
        }
        stats = new YFlowStats(yFlow, beforeTraffic, statsHelper)
    }

    @Tidy
    @Unroll
    def "System is able to collect #statsName stats and they grow monotonically"() {
        when: "Stats were collected"
        then: "#statsName stats is available"
        assert assertedStats(stats).isGrowingMonotonically(),
                "Collected statistics differs from expected. Actual:${assertedStats(stats)}"


        where:
        statsName                            | assertedStats
        "sub-flow 1 ingress forward packets" | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsForwardIngress() }
        "sub-flow 1 ingress reverse packets" | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsReverseIngress() }
        "sub-flow 1 egress forward packets"  | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsForwardEgress() }
        "sub-flow 1 egress reverse packets"  | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsReverseEgress() }
        "sub-flow 2 ingress forward packets" | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsForwardIngress() }
        "sub-flow 2 ingress reverse packets" | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsReverseIngress() }
        "sub-flow 2 egress forward packets"  | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsForwardEgress() }
        "sub-flow 2 egress reverse packets"  | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsReverseEgress() }
        "Y-Point packets"                    | { YFlowStats yfstats -> yfstats.getyPointPackets() }
        "Shared endndpoint packets"          | { YFlowStats yfstats -> yfstats.getSharedPackets() }
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
