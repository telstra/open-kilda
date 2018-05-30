package org.openkilda.atdd.staging.service.traffexam.model;

import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Value
@NonFinal
public class ExamReport {

    private final Exam exam;
    private final EndpointReport producerReport;
    private final EndpointReport consumerReport;

    public Bandwidth getBandwidth() {
        int kbps = producerReport.getBitsPerSecond().intValue() / 1024;
        return new Bandwidth(kbps);
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

    public boolean hasError() {
        return !StringUtils.isEmpty(producerReport.getError()) || !StringUtils.isEmpty(consumerReport.getError());
    }

    public boolean hasTraffic() {
        return 0 < producerReport.getBytes();
    }

    public boolean isTrafficLose() {
        return 0 < producerReport.getLostPackets() || 0 < consumerReport.getLostPackets();
    }
}
