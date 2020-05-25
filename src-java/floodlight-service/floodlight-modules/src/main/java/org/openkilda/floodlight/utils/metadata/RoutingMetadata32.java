/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.utils.metadata;

public class RoutingMetadata32 extends RoutingMetadata {
    /**
     * Must raise error for fields not available in 32 bits schema.
     */
    protected RoutingMetadata32(Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer inputPort) {
        super(lldpFlag, arpFlag, oneSwitchFlowFlag, inputPort);
    }
}
