/* Copyright 2019 Telstra Open Source
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

package org.openkilda.grpc.speaker.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogicalPortDto {

    @NonNull
    @JsonProperty("port_numbers")
    private List<Integer> portNumbers;

    @NonNull
    @JsonProperty("logical_port_number")
    private Integer logicalPortNumber;

    @NonNull
    @JsonProperty("logical_port_name")
    private String logicalPortName;

    @Builder
    @JsonCreator
    public LogicalPortDto(
            @JsonProperty("port_numbers") List<Integer> portNumbers,
            @JsonProperty("logical_port_number") Integer logicalPortNumber,
            @JsonProperty("logical_port_name") String logicalPortName) {
        setPortNumbers(portNumbers);
        setLogicalPortNumber(logicalPortNumber);
        setLogicalPortName(logicalPortName);
    }

    /**
     * Sets a port numbers.
     *
     * @param portNumbers list of a port numbers.
     */
    public void setPortNumbers(List<Integer> portNumbers) {
        if (portNumbers == null || portNumbers.isEmpty()) {
            throw new IllegalArgumentException("Need to set port numbers");
        }
        this.portNumbers = portNumbers;
    }

    /**
     * Sets a logical port number.
     *
     * @param logicalPortNumber a logical port number.
     */
    public void setLogicalPortNumber(Integer logicalPortNumber) {
        if (logicalPortNumber == null) {
            throw new IllegalArgumentException("Need to set logical port number");
        }
        this.logicalPortNumber = logicalPortNumber;
    }

    /**
     * Sets a logical port name.
     *
     * @param logicalPortName a logical port name.
     */
    public void setLogicalPortName(String logicalPortName) {
        if (logicalPortName == null) {
            throw new IllegalArgumentException("Need to set logical port name");
        }
        this.logicalPortName = logicalPortName;
    }
}
