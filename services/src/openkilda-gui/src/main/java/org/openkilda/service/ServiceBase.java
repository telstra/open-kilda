package org.openkilda.service;

import java.util.Date;

import org.openkilda.entity.Base;

/**
 * The Class ServiceBase.
 *
 * @author Gaurav Chugh
 */
public class ServiceBase {

    /**
     * Update entity flags.
     *
     * @param base the base
     * @param userId the user id
     * @return the base
     */
    public Base updateEntityFlags(Base base, int userId) {
        if (base != null) {
            base.setCreatedBy(Long.valueOf(userId));
            base.setUpdatedBy(Long.valueOf(userId));
            // Timestamp timestamp = new Timestamp(new Date().getTime());
            base.setCreatedDate(new Date());
            base.setUpdatedDate(new Date());
        }
        return base;
    }
}
