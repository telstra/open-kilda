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

package org.openkilda.log.constants;

import org.openkilda.log.dao.entity.ActivityTypeEntity;

public enum ActivityType {

    FLOW_VALIDATE(1L),
    FLOW_REROUTE(2L),
    SWITCH_RULES(3L),
    ISL_UPDATE_COST(4L),
    DELETE_PERMISSION(5L),
    CREATE_PERMISSION(6L),
    UPDATE_PERMISSION(7L),
    CREATE_ROLE(8L),
    DELETE_ROLE(9L),
    UPDATE_ROLE(10L),
    ASSIGN_ROLES_TO_PERMISSION(11L),
    CREATE_USER(12L),
    UPDATE_USER(13L),
    DELETE_USER(14L),
    ASSIGN_USERS_BY_ROLE(15L),
    CHANGE_PASSWORD(16L),
    RESET_PASSWORD(17L),
    ADMIN_RESET_PASSWORD(18L),
    RESET_2FA(19L),
    UPDATE_USER_SETTINGS(20L),
    CONFIGURE_SWITCH_PORT(21L),
    CREATE_FLOW(22L),
    UPDATE_FLOW(23L),
    DELETE_FLOW(24L),
    RESYNC_FLOW(25L),
    UPDATE_LINK_STORE_CONFIG(26L),
    UPDATE_OAUTH_CONFIG(27L),
    DELETE_LINK_STORE_CONFIG(28L),
    DELETE_CONTRACT(29L),
    UPDATE_SWITCH_STORE_CONFIG(30L),
    DELETE_SWITCH_STORE_CONFIG(31L),
    CONFIG_SESSION_TIMEOUT(32L);

    private Long id;
    private ActivityTypeEntity activityTypeEntity;

    /**
     * Instantiates a new activity type.
     *
     * @param id the id
     */
    private ActivityType(final Long id) {
        this.id = id;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * Gets the activity type entity.
     *
     * @return the activity type entity
     */
    public ActivityTypeEntity getActivityTypeEntity() {
        return activityTypeEntity;
    }

    /**
     * Sets the activity type entity.
     *
     * @param activityTypeEntity the new activity type entity
     */
    public void setActivityTypeEntity(final ActivityTypeEntity activityTypeEntity) {
        if (this.activityTypeEntity == null) {
            this.activityTypeEntity = activityTypeEntity;
        }
    }

    /**
     * Gets the activity by name.
     *
     * @param name the name
     * @return the activity by name
     */
    public static ActivityType getActivityByName(final String name) {
        ActivityType activityType = null;
        for (ActivityType type : ActivityType.values()) {
            if (type.getActivityTypeEntity().getActivityName().equalsIgnoreCase(name)) {
                activityType = type;
                break;
            }
        }
        return activityType;
    }

    /**
     * Gets the activity by id.
     *
     * @param id the id
     * @return the activity by id
     */
    public static ActivityType getActivityById(final long id) {
        ActivityType activityType = null;
        for (ActivityType type : ActivityType.values()) {
            if (type.getId().equals(id)) {
                activityType = type;
                break;
            }
        }
        return activityType;
    }
}
