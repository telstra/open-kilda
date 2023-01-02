package org.openkilda.functionaltests.spec.stats

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

import javax.inject.Provider

import static groovyx.gpars.GParsPool.withPool
import static groovyx.gpars.GParsPoolUtil.callAsync
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.junit.jupiter.api.Assumptions.assumeTrue

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
    int traffgenRunDuration = 20 //seconds
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
        def yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(switchTriplet))
        def beforeTraffic = new Date()
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildYFlowExam(yFlow, yFlow.maximumBandwidth, traffgenRunDuration)
        withPool {
            callAsync
                    {
                        Wrappers.safeRunSeveralTimes(3, traffgenRunDuration / 3) {
                            statsHelper."force kilda to collect stats"()
                        }
                    }
            [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                traffExam.waitExam(direction)
            }
        }
        stats = new YFlowStats(yFlow, beforeTraffic, statsHelper)
    }

    @Tidy
    def "System is able to collect #statsName stats"() {
        when: "Stats were collected"
        then: "#statsName stats is available"
        assert assertedStats(stats).isGrowingMonotonically(),
                "Collected statistics differs from expected. Actual:${assertedStats(stats)}"


        where:
        statsName                    | assertedStats                                                            | assertionMethod
        "sub-flow 1 ingress packets" | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsIngress() } | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 2 ingress packets" | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsIngress() } | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 1 egress packets"  | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsEgress() }  | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 2 egress packets"  | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsEgress() }  | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 1 packets"         | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPackets() }        | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 2 packets"         | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPackets() }        | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 1 raw packets"     | { YFlowStats yfstats -> yfstats.getSubFlow1Stats().getPacketsRaw() }     | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "sub-flow 2 raw packets"     | { YFlowStats yfstats -> yfstats.getSubFlow2Stats().getPacketsRaw() }     | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "Y-Point bytes"              | { YFlowStats yfstats -> yfstats.getyPointBytes() }                       | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
        "Shared endndpoint bytes"    | { YFlowStats yfstats -> yfstats.getSharedBytes() }                       | { StatsResult statsResult -> statsResult.isGrowingMonotonically() }
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
