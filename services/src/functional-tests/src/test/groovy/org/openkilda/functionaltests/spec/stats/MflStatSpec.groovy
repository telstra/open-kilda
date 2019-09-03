package org.openkilda.functionaltests.spec.stats

import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/fl-statistics")
@Narrative("""Now we have two FL instances: Management and Statistics.
- FL Stats: collect statistics only from the switches.
- FL Management: do the other work and can collect statistics as well when a switch doesn't connect to FL Stats.""")
class MflStatSpec extends HealthCheckSpecification {
    @Autowired
    FlowHelperV2 flowHelperV2

    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Tags(VIRTUAL)
    def "System is able to collect stats from the statistic and management controllers"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 100
        flowHelper.addFlow(flow)

        when: "Generate traffic on the given flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flow, (int) flow.maximumBandwidth)
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat in openTSDB is created"
        def metric = metricPrefix + "flow.raw.bytes"
        def tags = [switchid: srcSwitch.dpId.toOtsdFormat(), flowid: flow.id]
        // statsrouter.request.interval = 60, this test often fails on jenkins with this value
        // the statsrouter.request.interval is increased here to 120
        def statsRouterInterval = 120
        def waitInterval = 10
        def initStat
        Wrappers.wait(statsRouterInterval, waitInterval) {
            initStat = otsdb.query(startTime, metric, tags).dps
            assert initStat.size() >= 1
        }

        when: "Set only management controller on the src switch"
        lockKeeper.setController(srcSwitch, managementControllers[0])

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because management controller is set"
        def statFromMgmtController
        Wrappers.wait(statsRouterInterval, waitInterval) {
            statFromMgmtController = otsdb.query(startTime, metric, tags).dps
            assert statFromMgmtController.size() > initStat.size()
            assert statFromMgmtController.entrySet()[-2].value < statFromMgmtController.entrySet()[-1].value
        }

        when: "Set only statistic controller on the src switch"
        lockKeeper.setController(srcSwitch, statControllers[0])

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because statistic controller is set"
        def statFromStatsController
        Wrappers.wait(statsRouterInterval, waitInterval) {
            statFromStatsController = otsdb.query(startTime, metric, tags).dps
            assert statFromStatsController.size() > statFromMgmtController.size()
            assert statFromStatsController.entrySet()[-2].value < statFromStatsController.entrySet()[-1].value
        }

        when: "Disconnect the src switch from the management and statistic controllers"
        lockKeeper.knockoutSwitch(srcSwitch)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should not be collected because it is disconnected from controllers"
        double interval = statsRouterInterval * 0.2
        def statAfterDeletingControllers
        Wrappers.timedLoop(statsRouterInterval) {
            statAfterDeletingControllers = otsdb.query(startTime, metric, tags).dps
            assert statAfterDeletingControllers.size() == statFromStatsController.size()
            sleep((interval * 1000).toLong())
        }

        when: "Restore default controllers on the src switches"
        lockKeeper.reviveSwitch(srcSwitch)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.empty
        }

        then: "Old statistic should be collected"
        Wrappers.wait(statsRouterInterval, waitInterval) {
            def oldStats = otsdb.query(startTime, metric, tags).dps
            oldStats.size() > statAfterDeletingControllers.size()
            assert oldStats.entrySet()[-2].value < oldStats.entrySet()[-1].value
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Tags(VIRTUAL)
    def "System is able to collect stats from the statistic and management controllers (v2)"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 100
        flowHelperV2.addFlow(flow)

        when: "Generate traffic on the given flow"
        Date startTime = new Date()
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildExam(flowHelperV2.toV1(flow), (int) flow.maximumBandwidth)
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat in openTSDB is created"
        def metric = metricPrefix + "flow.raw.bytes"
        def tags = [switchid: srcSwitch.dpId.toOtsdFormat(), flowid: flow.flowId]
        // statsrouter.request.interval = 60, this test often fails on jenkins with this value
        // the statsrouter.request.interval is increased here to 120
        def statsRouterInterval = 120
        def waitInterval = 10
        def initStat
        Wrappers.wait(statsRouterInterval, waitInterval) {
            initStat = otsdb.query(startTime, metric, tags).dps
            assert initStat.size() >= 1
        }

        when: "Set only management controller on the src switch"
        lockKeeper.setController(srcSwitch, managementControllers[0])

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because management controller is set"
        def statFromMgmtController
        Wrappers.wait(statsRouterInterval, waitInterval) {
            statFromMgmtController = otsdb.query(startTime, metric, tags).dps
            assert statFromMgmtController.size() > initStat.size()
            assert statFromMgmtController.entrySet()[-2].value < statFromMgmtController.entrySet()[-1].value
        }

        when: "Set only statistic controller on the src switch"
        lockKeeper.setController(srcSwitch, statControllers[0])

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should be collected because statistic controller is set"
        def statFromStatsController
        Wrappers.wait(statsRouterInterval, waitInterval) {
            statFromStatsController = otsdb.query(startTime, metric, tags).dps
            assert statFromStatsController.size() > statFromMgmtController.size()
            assert statFromStatsController.entrySet()[-2].value < statFromStatsController.entrySet()[-1].value
        }

        when: "Disconnect the src switch from the management and statistic controllers"
        lockKeeper.knockoutSwitch(srcSwitch)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Stat on the src switch should not be collected because it is disconnected from controllers"
        double interval = statsRouterInterval * 0.2
        def statAfterDeletingControllers
        Wrappers.timedLoop(statsRouterInterval) {
            statAfterDeletingControllers = otsdb.query(startTime, metric, tags).dps
            assert statAfterDeletingControllers.size() == statFromStatsController.size()
            sleep((interval * 1000).toLong())
        }

        when: "Restore default controllers on the src switches"
        lockKeeper.reviveSwitch(srcSwitch)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.empty
        }

        then: "Old statistic should be collected"
        Wrappers.wait(statsRouterInterval, waitInterval) {
            def oldStats = otsdb.query(startTime, metric, tags).dps
            oldStats.size() > statAfterDeletingControllers.size()
            assert oldStats.entrySet()[-2].value < oldStats.entrySet()[-1].value
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }
}
