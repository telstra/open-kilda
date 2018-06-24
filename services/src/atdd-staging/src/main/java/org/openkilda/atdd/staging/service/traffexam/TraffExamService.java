package org.openkilda.atdd.staging.service.traffexam;

import org.openkilda.atdd.staging.service.traffexam.model.Exam;
import org.openkilda.atdd.staging.service.traffexam.model.ExamReport;
import org.openkilda.atdd.staging.service.traffexam.model.ExamResources;
import org.openkilda.atdd.staging.service.traffexam.model.Host;

import java.util.List;

public interface TraffExamService {

    List<Host> listHosts();

    Host hostByName(String name) throws NoResultsFoundException;

    ExamResources startExam(Exam exam)
            throws NoResultsFoundException, OperationalException;

    ExamReport waitExam(Exam exam);

    ExamReport waitExam(Exam exam, boolean cleanup);

    ExamReport fetchReport(Exam exam) throws NoResultsFoundException, ExamNotFinishedException;

    void stopExam(Exam exam) throws NoResultsFoundException;

    void stopAll();
}
