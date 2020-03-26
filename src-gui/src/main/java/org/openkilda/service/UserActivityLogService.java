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

package org.openkilda.service;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.log.model.LogInfo;
import org.openkilda.log.service.UserActivityService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.util.ValidatorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                if (userEntity != null) {
                    logs.get(i).setUsername(userEntity.getUsername());
                } else {
                    logs.get(i).setUsername(String.valueOf(logs.get(i).getUserId()));
                }
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
    public UserEntity getUser(final long userId, final List<UserEntity> users) {
        for (UserEntity userEntity : users) {
            if (userEntity.getUserId() == userId) {
                return userEntity;
            }
        }
        return null;
    }
}
