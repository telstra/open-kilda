package org.openkilda.atdd.staging.model.traffexam;

public class EndpointReport {
    private Long packets = 0L;
    private Long bytes = 0L;

    private Long lostPackets = 0L;
    private Float lostPercent = 0f;

    private Double seconds = 0d;
    private Double bitsPerSecond = 0d;

    private String error = null;

    public EndpointReport(ReportResponse report) {
        error = report.getError();

        try {
            IPerfReportSumBranch summary = report.getReport().end.sum;
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

    public Long getPackets() {
        return packets;
    }

    public Long getBytes() {
        return bytes;
    }

    public Long getLostPackets() {
        return lostPackets;
    }

    public Float getLostPercent() {
        return lostPercent;
    }

    public Double getSeconds() {
        return seconds;
    }

    public Double getBitsPerSecond() {
        return bitsPerSecond;
    }

    public String getError() {
        return error;
    }
}
