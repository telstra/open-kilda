/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.info.switches.v2;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class LogicalPortsValidationEntryV2 implements Serializable {
    private boolean asExpected;
    private String error;
    private List<LogicalPortInfoEntryV2> excess;
    private List<LogicalPortInfoEntryV2> proper;
    private List<LogicalPortInfoEntryV2> missing;
    private List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfigured;
}
