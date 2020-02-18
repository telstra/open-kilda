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

package org.openkilda.wfm.topology.network.service;

import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;

public interface IUniIslCarrier {
    void setupIslFromHistory(Endpoint endpoint, IslReference islReference, Isl history);

    void notifyIslUp(Endpoint endpoint, IslReference reference,
                     IslDataHolder islData);

    void notifyIslDown(Endpoint endpoint, IslReference reference, IslDownReason reason);

    void notifyIslMove(Endpoint endpoint, IslReference reference);

    void notifyIslRoundTripStatus(IslReference reference, RoundTripStatus roundTripStatus);
}
