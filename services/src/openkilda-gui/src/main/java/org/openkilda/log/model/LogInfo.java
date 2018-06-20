package org.openkilda.log.model;

import java.util.Date;

import org.openkilda.log.constants.ActivityType;

public class LogInfo {

    private long userId;
    
    private String username;

    private ActivityType activityType;

    private String objectId;

    private Date activityTime;

    private String clientIpAddress;

    public long getUserId() {
        return userId;
    }

    public void setUserId(final long userId) {
        this.userId = userId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(final ActivityType activityType) {
        this.activityType = activityType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(final String objectId) {
        this.objectId = objectId;
    }

    public Date getActivityTime() {
        return activityTime;
    }

    public void setActivityTime(final Date activityTime) {
        this.activityTime = activityTime;
    }

    public String getClientIpAddress() {
        return clientIpAddress;
    }

    public void setClientIpAddress(final String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
    }

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
    
}
