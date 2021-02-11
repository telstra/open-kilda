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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.InfoData;

import java.util.List;

public interface FlowHubCarrier {

    void sendCommandToSpeakerWorker(String key, CommandData commandData);

    void sendToResponseSplitterBolt(String key, List<? extends InfoData> message);

    void sendToMessageEncoder(String key, ErrorData errorData);

    void endProcessing(String key);

    void sendInactive();
}
