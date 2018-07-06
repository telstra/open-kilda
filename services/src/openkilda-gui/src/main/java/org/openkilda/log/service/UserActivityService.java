package org.openkilda.log.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.openkilda.log.constants.ActivityType;
import org.openkilda.log.dao.entity.UserActivityEntity;
import org.openkilda.log.dao.repository.UserActivityRepository;
import org.openkilda.log.model.LogInfo;
import org.openkilda.log.util.LogConversionUtil;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class UserActivityService {

    @Autowired
    private MessageUtils messageUtil;

	@Autowired
	private UserActivityRepository userActivityRepository;

	@Value("${log.duration}")
	private int logDuration;

	@Transactional(readOnly = false)
	public void logUserActivity(final LogInfo logInfo) {
		UserActivityEntity userActivityEntity = LogConversionUtil.getUserActivity(logInfo);
		userActivityRepository.save(userActivityEntity);
	}

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

	public Date getDefaultStartDate() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -logDuration);
		return cal.getTime();
	}

	public void validateLogRequest(final Date startTime, final Date endTime) {
		if (startTime.after(endTime)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("Start Time", startTime + ""));
		}
	}
}
