/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.testlib.service.traffexam.model;

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
