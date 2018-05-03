package org.openkilda.atdd.staging.service.traffexam.model;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

@Getter
public class EndpointReport {
    private Long packets = 0L;
    private Long bytes = 0L;

    private Long lostPackets = 0L;
    private Float lostPercent = 0f;

    private Double seconds = 0d;
    private Double bitsPerSecond = 0d;

    private final String error;

    public EndpointReport(String error) {
        this.error = error;
    }

    public EndpointReport(ReportResponse report) {
        if (Strings.isEmpty(report.getError())) {
            this.error = report.getReport().error;
        } else {
            this.error = report.getError();
        }

        IPerfReportEndBranch finalResults = report.getReport().end;
        if (finalResults.sumReceived != null) {
            unpackTcpReport(finalResults.sumReceived);
        } else if (finalResults.sumSent != null) {
            unpackTcpReport(finalResults.sumSent);
        } else if (finalResults.sum != null) {
            unpackUdpReport(finalResults);
        }
    }

    private void unpackTcpReport(IPerfReportTcpSumSection finalResults) {
        bytes = finalResults.getBytes();
        seconds = finalResults.getEnd() - finalResults.getStart();
        bitsPerSecond = finalResults.getBitsPerSecond();
    }

    private void unpackUdpReport(IPerfReportEndBranch finalResults) {
        try {
            IPerfReportSumBranch summary = finalResults.sum;
            packets = summary.packets;
            bytes = summary.bytes;
            lostPackets = summary.lostPackets;
            lostPercent = summary.lostPercent;
            seconds = summary.seconds;
            bitsPerSecond = summary.bitsPerSecond;
        } catch (NullPointerException e) {
            // skip initialisation it there is no summary data
        }
    }
}
