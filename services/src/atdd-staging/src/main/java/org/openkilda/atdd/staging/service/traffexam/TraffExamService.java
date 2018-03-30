package org.openkilda.atdd.staging.service.traffexam;

import org.openkilda.atdd.staging.service.traffexam.model.Exam;
import org.openkilda.atdd.staging.service.traffexam.model.ExamReport;
import org.openkilda.atdd.staging.service.traffexam.model.Host;

import java.util.List;

public interface TraffExamService {
    List<Host> listHosts();
    Host hostByName(String name) throws NoResultsFoundException;

    Exam startExam(Exam exam)
            throws NoResultsFoundException, OperationalException;

    List<ExamReport> waitExam(List<Exam> exams);

    List<ExamReport> waitExam(List<Exam> exams, boolean cleanup);

    ExamReport fetchReport(Exam exam) throws NoResultsFoundException, ExamNotFinishedException;

    void stopExam(Exam exam) throws NoResultsFoundException;

    void stopAll();
}
