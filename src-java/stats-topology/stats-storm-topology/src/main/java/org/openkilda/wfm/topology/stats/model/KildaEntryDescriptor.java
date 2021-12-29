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

package org.openkilda.wfm.topology.stats.model;

import org.openkilda.model.SwitchId;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.io.Serializable;

@Getter
@ToString
@EqualsAndHashCode
public abstract class KildaEntryDescriptor implements Serializable {
    @NonNull
    protected final SwitchId switchId;

    @NonNull
    protected final MeasurePoint measurePoint;

    public KildaEntryDescriptor(@NonNull SwitchId switchId, @NonNull MeasurePoint measurePoint) {
        this.switchId = switchId;
        this.measurePoint = measurePoint;
    }

    public abstract void handle(KildaEntryDescriptorHandler handler);
}
