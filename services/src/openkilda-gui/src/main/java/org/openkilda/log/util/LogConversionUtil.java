package org.openkilda.log.util;

import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.dao.entity.UserActivityEntity;
import org.openkilda.log.model.LogInfo;

public final class LogConversionUtil {

    private LogConversionUtil() {

    }

    public static UserActivityEntity getUserActivity(final LogInfo info) {
        UserActivityEntity userActivity = new UserActivityEntity();
        userActivity.setUserId(info.getUserId());
        userActivity.setActivity(info.getActivityType().getActivityTypeEntity());
        userActivity.setObjectId(info.getObjectId());
        userActivity.setActivityTime(info.getActivityTime());
        userActivity.setClientIp(info.getClientIpAddress());
        return userActivity;
    }
    
    public static LogInfo getLogInfo(final UserActivityEntity userActivity) {
    	LogInfo info = new LogInfo();
        info.setUserId(userActivity.getUserId());
        info.setActivityType(ActivityType.getActivityById(userActivity.getActivity().getId()));
        info.setObjectId(userActivity.getObjectId());
        info.setActivityTime(userActivity.getActivityTime());
        info.setClientIpAddress(userActivity.getClientIp());
        return info;
    }
}
