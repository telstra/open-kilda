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

package org.openkilda.pce;

import org.openkilda.pce.finder.FailReason;
import org.openkilda.pce.finder.FailReasonType;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.io.Serializable;
import java.util.Map;

@Value
@Builder
public class GetPathsResult implements Serializable {
    private static final long serialVersionUID = 2217277881039083324L;
    Path forward;
    Path reverse;
    boolean backUpPathComputationWayUsed;

    @Singular
    Map<FailReasonType, FailReason> failReasons;

    public boolean isSuccess() {
        return (failReasons == null || failReasons.isEmpty()) && forward != null;
    }
}
