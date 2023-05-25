package org.openkilda.functionaltests.helpers.model.traffic.ha


import org.openkilda.testing.service.traffexam.model.ExamReport

class HaFlowExamReport {

    private ExamReport forward1Report
    private ExamReport forward2Report
    private ExamReport reverse1Report
    private ExamReport reverse2Report

    HaFlowExamReport(ExamReport forward1Report, ExamReport forward2Report, ExamReport reverse1Report, ExamReport reverse2Report) {
        this.forward1Report = forward1Report
        this.forward2Report = forward2Report
        this.reverse1Report = reverse1Report
        this.reverse2Report = reverse2Report
    }

    boolean hasTraffic() {
        return toIterable().every {it.hasTraffic()}
    }

    private List<ExamReport> toIterable() {
        return [forward1Report, forward2Report, reverse1Report, reverse2Report]
    }
}
