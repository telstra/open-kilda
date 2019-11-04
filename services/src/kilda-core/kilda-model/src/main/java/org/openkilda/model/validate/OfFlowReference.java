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

package org.openkilda.model.validate;

import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.FlowSegmentSchema;
import org.openkilda.model.of.OfFlowSchema;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.io.Serializable;

@Value
@AllArgsConstructor
public class OfFlowReference implements Serializable {
    private final SwitchId switchId;
    private final int tableId;
    private final Cookie cookie;

    public OfFlowReference(FlowSegmentSchema schema, OfFlowSchema entry) {
        this(schema.getSwitchId(), entry.getTableId(), entry.getCookie());
    }

    public OfFlowReference(SwitchId switchId, OfFlowSchema entry) {
        this(switchId, entry.getTableId(), entry.getCookie());
    }
}
