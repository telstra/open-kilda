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

public class FlowSegmentBlankResolver<T extends FlowSegmentRequest> implements IFlowSegmentBlank<T> {
    private final IFlowSegmentBlank<T> blank;

    public FlowSegmentBlankResolver(IFlowSegmentBlank<T> blank) {
        this.blank = blank;
    }

    @Override
    public T makeInstallRequest() {
        return blank.makeInstallRequest();
    }

    @Override
    public T makeRemoveRequest() {
        return blank.makeRemoveRequest();
    }

    @Override
    public T makeVerifyRequest() {
        return blank.makeVerifyRequest();
    }

    @Override
    public UUID getCommandId() {
        return blank.getCommandId();
    }

    @Override
    public SwitchId getSwitchId() {
        return blank.getSwitchId();
    }

    public FlowSegmentBlankGenericResolver makeGenericResolver() {
        return new FlowSegmentBlankGenericResolver(blank);
    }
}
