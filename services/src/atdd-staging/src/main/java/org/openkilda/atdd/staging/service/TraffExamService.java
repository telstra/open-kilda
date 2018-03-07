package org.openkilda.atdd.staging.service;

import org.openkilda.atdd.staging.model.traffexam.Exam;
import org.openkilda.atdd.staging.model.traffexam.ExamReport;
import org.openkilda.atdd.staging.model.traffexam.Host;

import java.util.List;
import javax.naming.directory.InvalidAttributesException;

public interface TraffExamService {
    List<Host> listHosts();
    Host hostByName(String name)
            throws InvalidAttributesException, NoResultsFoundException;

    Exam startExam(Exam exam)
            throws NoResultsFoundException, OperationalException;

    ExamReport fetchReport(Exam exam) throws NoResultsFoundException, ExamNotFinishedException;

    void stopExam(Exam exam) throws NoResultsFoundException;

    void stopAll();
}
