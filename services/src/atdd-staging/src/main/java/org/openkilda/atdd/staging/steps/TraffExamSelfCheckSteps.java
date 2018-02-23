package org.openkilda.atdd.staging.steps;

import org.openkilda.atdd.staging.model.traffexam.Exam;
import org.openkilda.atdd.staging.model.traffexam.Host;
import org.openkilda.atdd.staging.model.traffexam.Report;
import org.openkilda.atdd.staging.service.ExamNotFinishedException;
import org.openkilda.atdd.staging.service.NoResultsFoundException;
import org.openkilda.atdd.staging.service.OperationalException;
import org.openkilda.atdd.staging.service.TraffExamService;

import cucumber.api.java.en.Then;
import cucumber.api.java8.En;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;
import javax.naming.directory.InvalidAttributesException;

public class TraffExamSelfCheckSteps implements En {
    @Autowired
    private TraffExamService traffExam;

    @Then("setup and check traffic exam")
    public void simpleTest()
            throws OperationalException, NoResultsFoundException, InterruptedException, InvalidAttributesException, ExamNotFinishedException {
        Host sourceHost = traffExam.hostByName("tg1");
        Host destHost = traffExam.hostByName("tg2");

        Exam exam = traffExam.startExam(sourceHost, destHost);
        TimeUnit.SECONDS.sleep(11);

        Report reportProducer = traffExam.fetchReport(exam.source);
        Report reportConsumer = traffExam.fetchReport(exam.dest);

        System.out.println(String.format("Producer status: %s", reportProducer.getStatus()));
        System.out.println(String.format("Consumer status: %s", reportConsumer.getStatus()));
    }
}
