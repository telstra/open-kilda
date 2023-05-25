package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.helpers.HaFlowHelper
import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Slf4j
@Narrative("Verify the ability to create diverse y-flows in the system.")
class HaFlowTraffSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Tidy
    def "Able to create diverse y-flows"() {
        given: "Switches with three not overlapping paths at least"
        def swT = topologyHelper.switchTriplets.find {
            [it.shared, it.ep1, it.ep2].every { it.traffGens } &&
                    [it.pathsEp1, it.pathsEp2].every {
                        it.collect { pathHelper.getInvolvedIsls(it) }
                                .unique { a, b -> a.intersect(b) ? 0 : 1 }.size() >= 3
                    }
        }
        assumeTrue(swT != null, "Unable to find suitable switches")

        when: "Create three ha-flow"
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        and: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildHaFlowExam(haFlow, haFlow.maximumBandwidth, 10)
        examReports = withPool {
            [exam.forward1, exam.forward2, exam.reverse1, exam.reverse2].collectParallel { Exam direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                traffExam.waitExam(direction)
            }
        }

        then: "Traffic flows on both sub-flows, but does not exceed the y-flow bandwidth restriction (~halves for each sub-flow)"
        examReports.each { report ->
            assert report.hasTraffic(), report.exam
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }


}