package org.openkilda.functionaltests.helpers.model.traffic.ha;

import com.google.common.collect.ImmutableList
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Exam

import static groovyx.gpars.GParsExecutorsPool.withPool;

class HaFlowBidirectionalExam {

    private Exam forward1;
    private Exam forward2;
    private Exam reverse1;
    private Exam reverse2;

    TraffExamService traffExam

    HaFlowBidirectionalExam(TraffExamService traffExam, Exam forward1, Exam forward2, Exam reverse1, Exam reverse2) {
        this.traffExam = traffExam
        this.forward1 = forward1
        this.forward2 = forward2
        this.reverse1 = reverse1
        this.reverse2 = reverse2
    }

    List<Exam> getAllExams() {
        return ImmutableList.of(forward1, reverse1, forward2, reverse2);
    }

    HaFlowExamReport run() {
        def reports = withPool {
            [forward1, forward2, reverse1, reverse2].collectParallel { Exam direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                return traffExam.waitExam(direction)
            }
        }
        return new HaFlowExamReport(reports[0], reports[1], reports[2], reports[3])
    }
}
