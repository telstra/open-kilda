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

    /**
     * Get list of errors that appeared during traff exam.
     */
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
