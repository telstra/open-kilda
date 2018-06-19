package org.openkilda.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.log.model.LogInfo;
import org.openkilda.log.service.UserActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.util.ValidatorUtil;

@Service("userActivityLogService")
public class UserActivityLogService {

	@Autowired
	private UserActivityService userActivityService;
	
	@Autowired
	private UserRepository userRepository;
	
    @Autowired
    private ServerContext serverContext;
	
	/**
	 * Gets the activity log.
	 *
	 * @param users the users
	 * @param activities the activities
	 * @param start the start
	 * @param end the end
	 * @return the activity log
	 */
	public List<LogInfo> getActivityLog(final List<Long> users, final List<String> activities, final String start,
			final String end) {
		List<LogInfo> logs = userActivityService.getLogs(users, activities, start, end);
		List<LogInfo> appAdminlogs = new ArrayList<LogInfo>();
		if (!ValidatorUtil.isNull(logs)) {
			Set<Long> userIds = new HashSet<Long>();
			for (LogInfo log : logs) {
				if (serverContext.getRequestContext().getUserId() != 1 && log.getUserId() == 1) {
					appAdminlogs.add(log);
				}
				userIds.add(log.getUserId());
			}
			logs.removeAll(appAdminlogs);
			
			List<UserEntity> usersList = userRepository.findByUserIdIn(userIds);
			for (int i = 0; i < logs.size(); i++) {
				UserEntity userEntity = getUser(logs.get(i).getUserId(), usersList);
				logs.get(i).setUsername(userEntity.getUsername());
			}
		}
		return logs;
	}
	
    /**
     * Gets the user.
     *
     * @param userId the user id
     * @param users the users
     * @return the user
     */
    public UserEntity getUser(final long userId, final List<UserEntity> users){
		for (UserEntity userEntity : users) {
			if (userEntity.getUserId() == userId) {
				return userEntity;
			}
		}
		return null;
    }
}
