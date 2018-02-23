package org.openkilda.atdd.staging.model.traffexam;

import org.springframework.util.StringUtils;

public class ExamReport {
    private final EndpointReport producerReport;
    private final EndpointReport consumerReport;

    public ExamReport(EndpointReport producerReport, EndpointReport consumerReport) {
        this.producerReport = producerReport;
        this.consumerReport = consumerReport;
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
