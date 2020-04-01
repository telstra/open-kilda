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

package org.openkilda.log.service;

import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.dao.entity.UserActivityEntity;
import org.openkilda.log.dao.repository.UserActivityRepository;
import org.openkilda.log.model.LogInfo;
import org.openkilda.log.util.LogConversionUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * The Class UserActivityService.
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class UserActivityService {

    /** The message util. */
    @Autowired
    private MessageUtils messageUtil;

    /** The user activity repository. */
    @Autowired
    private UserActivityRepository userActivityRepository;

    /** The log duration. */
    @Value("${log.duration}")
    private int logDuration;

    /**
     * Log user activity.
     *
     * @param logInfo the log info
     */
    @Transactional(readOnly = false)
    public void logUserActivity(final LogInfo logInfo) {
        UserActivityEntity userActivityEntity = LogConversionUtil.getUserActivity(logInfo);
        userActivityRepository.save(userActivityEntity);
    }

    /**
     * Gets the logs.
     *
     * @param users the users
     * @param activities the activities
     * @param start the start
     * @param end the end
     * @return the logs
     */
    @Transactional(readOnly = true)
    public List<LogInfo> getLogs(final List<Long> users, List<String> activities, final String start,
            final String end) {
        activities = (ValidatorUtil.isNull(activities) || activities.contains("all")) ? null : activities;
        List<Long> activityIds = new ArrayList<Long>();
        if (!ValidatorUtil.isNull(activities)) {
            for (String activity : activities) {
                ActivityType activityType = ActivityType.getActivityByName(activity.trim());
                if (activityType != null) {
                    activityIds.add(activityType.getId());
                }
            }
        }

        Date startTime = start != null ? new Date(Long.parseLong(start)) : getDefaultStartDate();
        Date endTime = end != null ? new Date(Long.parseLong(end)) : Calendar.getInstance().getTime();

        validateLogRequest(startTime, endTime);
        return getLogs(users, activityIds, startTime, endTime);
    }

    /**
     * Gets the logs.
     *
     * @param userIds the user ids
     * @param activityIds the activity ids
     * @param startTime the start time
     * @param endTime the end time
     * @return the logs
     */
    private List<LogInfo> getLogs(final List<Long> userIds, final List<Long> activityIds, final Date startTime,
            final Date endTime) {
        List<UserActivityEntity> userActivityEntityList = new ArrayList<>();

        if (ValidatorUtil.isNull(userIds) && ValidatorUtil.isNull(activityIds)) {
            userActivityEntityList = userActivityRepository
                    .findByActivityTimeGreaterThanEqualAndActivityTimeLessThanEqual(startTime, endTime);
        } else if (!ValidatorUtil.isNull(userIds) && ValidatorUtil.isNull(activityIds)) {
            userActivityEntityList = userActivityRepository
                    .findByUserIdInAndActivityTimeGreaterThanEqualAndActivityTimeLessThanEqual(userIds, startTime,
                            endTime);
        } else if (!ValidatorUtil.isNull(userIds) && !ValidatorUtil.isNull(activityIds)) {
            userActivityEntityList = userActivityRepository
                    .findByUserIdInAndActivityTimeGreaterThanEqualAndActivityTimeLessThanEqualAndActivity_IdIn(userIds,
                            startTime, endTime, activityIds);
        } else if (ValidatorUtil.isNull(userIds)) {
            userActivityEntityList = userActivityRepository
                    .findByActivityTimeGreaterThanEqualAndActivityTimeLessThanEqualAndActivity_IdIn(startTime, endTime,
                            activityIds);
        }

        List<LogInfo> logs = new ArrayList<LogInfo>();
        for (UserActivityEntity userActivityEntity : userActivityEntityList) {
            logs.add(LogConversionUtil.getLogInfo(userActivityEntity));
        }
        return logs;
    }

    /**
     * Gets the default start date.
     *
     * @return the default start date
     */
    public Date getDefaultStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -logDuration);
        return cal.getTime();
    }

    /**
     * Validate log request.
     *
     * @param startTime the start time
     * @param endTime the end time
     */
    public void validateLogRequest(final Date startTime, final Date endTime) {
        if (startTime.after(endTime)) {
            throw new RequestValidationException(messageUtil.getAttributeInvalid("Start Time", startTime + ""));
        }
    }
}
