package org.openkilda.atdd.staging.service.traffexam.model;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ExamReport {
    private final Exam exam;
    private final EndpointReport producerReport;
    private final EndpointReport consumerReport;

    public ExamReport(Exam exam, EndpointReport producerReport, EndpointReport consumerReport) {
        this.exam = exam;
        this.producerReport = producerReport;
        this.consumerReport = consumerReport;
    }

    public Exam getExam() {
        return exam;
    }

    public Bandwidth getBandwidth() {
        return new Bandwidth(producerReport.getBitsPerSecond().intValue());
    }

    public List<String> getErrors() {
        List<String> errors = new ArrayList<>(2);
        if (!StringUtils.isEmpty(producerReport.getError())) {
            errors.add(String.format("producer: %s", producerReport.getError()));
        }
        if (!StringUtils.isEmpty(consumerReport.getError())) {
            errors.add(String.format("consumer: %s", consumerReport.getError()));
        }

        return errors;
    }

    public boolean isError() {
        return !StringUtils.isEmpty(producerReport.getError()) || !StringUtils.isEmpty(consumerReport.getError());
    }

    public boolean isTraffic() {
        return 0 < producerReport.getPackets() && 0 < consumerReport.getPackets();
    }

    public boolean isTrafficLose() {
        return 0 < producerReport.getLostPackets() || 0 < consumerReport.getLostPackets();
    }
}
