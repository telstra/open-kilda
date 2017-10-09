/* Copyright 2017 Telstra Open Source
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

package org.openkilda.topology.service;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.topology.domain.Switch;

/**
 * Service for operations on switches.
 */
public interface SwitchService {
    /**
     * Returns switch.
     *
     * @param switchId switch datapath id
     * @return found {@link Switch} instance or null
     */
    Switch get(final String switchId);

    /**
     * Returns all switches.
     *
     * @return all found {@link Switch} instances
     */
    Iterable<Switch> dump();

    /**
     * Adds switch.
     *
     * @param data {@link SwitchInfoData} message data
     * @return added {@link Switch} instance
     */
    Switch add(final SwitchInfoData data);

    /**
     * Deletes switch.
     *
     * @param data {@link SwitchInfoData} message data
     * @return removed {@link Switch} instance
     */
    Switch remove(final SwitchInfoData data);

    /**
     * Activates switch.
     *
     * @param data {@link SwitchInfoData} message data
     * @return modified {@link Switch} instance
     */
    Switch activate(final SwitchInfoData data);

    /**
     * Deactivates switch.
     *
     * @param data {@link SwitchInfoData} message data
     * @return modified {@link Switch} instance
     */
    Switch deactivate(final SwitchInfoData data);

    /**
     * Changes swotch.
     *
     * @param data {@link SwitchInfoData} message data
     * @return modified {@link Switch} instance
     */
    Switch change(final SwitchInfoData data);
}
