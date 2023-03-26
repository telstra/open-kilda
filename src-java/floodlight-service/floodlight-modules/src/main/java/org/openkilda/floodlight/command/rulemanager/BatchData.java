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

package org.openkilda.floodlight.command.rulemanager;

import org.openkilda.rulemanager.SpeakerData;

import lombok.Builder;
import lombok.Data;
import org.projectfloodlight.openflow.protocol.OFMessage;

@Data
@Builder
public class BatchData {
    private boolean meter;
    private boolean group;
    private boolean flow;
    private OFMessage message;
    private SpeakerData origin;
    private boolean presenceBeVerified;
}
