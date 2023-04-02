/* Copyright 2022 Telstra Open Source
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

package org.openkilda.pce.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class FailReason {

    FailReasonType type;
    String additionalInfo;
    Long weight;

    public FailReason(FailReasonType type) {
        this(type, null, null);
    }

    public FailReason(FailReasonType type, Long weight) {
        this(type, null, weight);
    }

    public FailReason(FailReasonType type, String additionalInfo) {
        this(type, additionalInfo, null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(type.toString());
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            sb.append(": ").append(additionalInfo);
        }
        return sb.toString();
    }

}
