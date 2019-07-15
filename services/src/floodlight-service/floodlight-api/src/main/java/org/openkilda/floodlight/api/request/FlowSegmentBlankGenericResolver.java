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

package org.openkilda.floodlight.api.request;

import org.openkilda.model.SwitchId;

import java.util.UUID;

public class FlowSegmentBlankGenericResolver {
    private final IFlowSegmentBlank<? extends FlowSegmentRequest> blank;

    public FlowSegmentBlankGenericResolver(
            IFlowSegmentBlank<? extends FlowSegmentRequest> blank) {
        this.blank = blank;
    }

    public FlowSegmentRequest makeInstallRequest() {
        return blank.makeInstallRequest();
    }

    public FlowSegmentRequest makeRemoveRequest() {
        return blank.makeRemoveRequest();
    }

    public FlowSegmentRequest makeVerifyRequest() {
        return blank.makeVerifyRequest();
    }

    public UUID getCommandId() {
        return blank.getCommandId();
    }

    public SwitchId getSwitchId() {
        return blank.getSwitchId();
    }
}
