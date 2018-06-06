package org.openkilda.log.constants;

import org.openkilda.log.dao.entity.ActivityTypeEntity;

public enum ActivityType {

    FLOW_VALIDATE(1L),
    FLOW_REROUTE(2L),
    SWITCH_RULES(3L),
    ISL_UPDATE_COST(4L);

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
