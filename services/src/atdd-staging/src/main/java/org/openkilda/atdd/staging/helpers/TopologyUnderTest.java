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

package org.openkilda.atdd.staging.helpers;

import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.testing.model.topology.TopologyDefinition;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class TopologyUnderTest {
    Map<FlowPayload, List<TopologyDefinition.Isl>> flowIsls = new HashMap<>();
    Map<String, Object> aliasedObjects = new HashMap<>();

    public void addAlias(String alias, Object obj) {
        aliasedObjects.put(alias, obj);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAliasedObject(String alias) {
        return (T) aliasedObjects.get(alias);
    }

    /**
     * Get all aliased objects of certain type.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getAliasedObjects(Class<T> clazz) {
        return aliasedObjects.values().stream()
                .filter(clazz::isInstance)
                .map(sw -> (T) sw).collect(Collectors.toList());
    }
}
