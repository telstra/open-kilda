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

package org.openkilda.atdd.staging.service.aswitch;

import org.openkilda.atdd.staging.service.aswitch.model.ASwitchFlow;

import java.util.List;

public interface ASwitchService {
    void addFlows(List<ASwitchFlow> flows);

    void removeFlows(List<ASwitchFlow> flows);

    List<ASwitchFlow> getAllFlows();

    void portsUp(List<Integer> ports);

    void portsDown(List<Integer> ports);
}
