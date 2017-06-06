package org.bitbucket.openkilda.topology.service;

import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.topology.domain.Switch;

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
