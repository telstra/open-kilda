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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.Cookie;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/**
 * A base for path info messages.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
abstract class BaseFlowPathInfo extends InfoData {
    @NonNull String flowId;
    @NonNull Cookie cookie;
    MeterId meterId;
    @NonNull List<PathNodePayload> pathNodes;
}
