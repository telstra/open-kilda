package org.bitbucket.openkilda.topology.service;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.topology.domain.Isl;

/**
 * Service for operations on links.
 */
public interface IslService {
    /**
     * Updates isl.
     *
     * @param data {@link IslInfoData} message data
     */
    void discoverLink(final IslInfoData data);

    /**
     * Removes isl.
     *
     * @param data {@link IslInfoData} message data
     */
    void dropLink(final IslInfoData data);

    /**
     * Get isl.
     *
     * @param data {@link IslInfoData} message data
     * @return {@link Isl} instance
     */
    Isl getLink(final IslInfoData data);
}
