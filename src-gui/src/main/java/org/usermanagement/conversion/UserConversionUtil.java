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

package org.usermanagement.conversion;

import org.openkilda.constants.Status;
import org.openkilda.utility.StringUtil;

import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.StatusEntity;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.ValidatorUtil;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Class UserConversionUtil.
 */

public final class UserConversionUtil {

    private static String password = "Admin@2018";
    
    private UserConversionUtil() {

    }

    /**
     * To user entity.
     *
     * @param userInfo the user info
     * @param roleEntities the role entities
     * @return the user entity
     */
    public static UserEntity toUserEntity(final UserInfo userInfo, final Set<RoleEntity> roleEntities) {

        UserEntity userEntity = new UserEntity();

        userEntity.setUsername(userInfo.getUsername().toLowerCase());
        userEntity.setPassword(StringUtil.encodeString(password));
        userEntity.setEmail(userInfo.getEmail().toLowerCase());
        userEntity.setName(userInfo.getName());
        userEntity.setRoles(roleEntities);
        userEntity.setActiveFlag(true);
        userEntity.setLoginTime(new Timestamp(System.currentTimeMillis()));
        userEntity.setLogoutTime(new Timestamp(System.currentTimeMillis()));
        userEntity.setIsAuthorized(true);
        userEntity.setIs2FaEnabled(true);
        userEntity.setIs2FaConfigured(false);
        StatusEntity statusEntity = Status.ACTIVE.getStatusEntity();
        userEntity.setStatusEntity(statusEntity);
        return userEntity;
    }

    /**
     * To user info.
     *
     * @param userEntity the user entity
     * @return the user info
     */
    public static UserInfo toUserInfo(final UserEntity userEntity) {
        UserInfo userInfo = new UserInfo();
        userInfo.setName(userEntity.getName());
        userInfo.setEmail(userEntity.getEmail().toLowerCase());
        userInfo.setUsername(userEntity.getUsername().toLowerCase());
        userInfo.setIs2FaEnabled(userEntity.getIs2FaEnabled());
        userInfo.setStatus(userEntity.getStatusEntity().getStatus());
        userInfo.setUserId(userEntity.getUserId());
        Set<String> roles = new HashSet<>();

        if (!ValidatorUtil.isNull(userEntity.getRoles())) {
            for (RoleEntity roleEntity : userEntity.getRoles()) {
                roles.add(roleEntity.getName());
            }
            userInfo.setRoles(roles);
        }
        return userInfo;
    }

    /**
     * To all users.
     *
     * @param userEntityList the user entity list
     * @return the list
     */
    public static List<UserInfo> toAllUsers(final List<UserEntity> userEntityList) {
        List<UserInfo> userList = new ArrayList<>();

        for (UserEntity userEntity : userEntityList) {
            if (userEntity.getUserId() != 1) {
                userList.add(toUserInfo(userEntity));
            }
        }
        return userList;
    }

    /**
     * To upate user entity.
     *
     * @param userInfo the user info
     * @param userEntity the user entity
     */
    public static void toUpateUserEntity(final UserInfo userInfo, final UserEntity userEntity,
            final StringBuilder activityMessage) {
        if (!ValidatorUtil.isNull(userInfo.getName())) {
            userEntity.setName(userInfo.getName());
            activityMessage.append("username:" + userInfo.getName() + "\n");
        }

        if (!ValidatorUtil.isNull(userInfo.getStatus())) {
            Status status = Status.getStatusByName(userInfo.getStatus());
            if (status == Status.ACTIVE) {
                userEntity.setActiveFlag(true);
            } else if (status == Status.INACTIVE) {
                userEntity.setActiveFlag(false);
            }
            userEntity.setStatusEntity(status.getStatusEntity());
            activityMessage.append("status:" + userInfo.getStatus() + "\n");
        }
        if (!ValidatorUtil.isNull(userInfo.getIs2FaEnabled())) {
            if (!userInfo.getIs2FaEnabled()) {
                userEntity.setIs2FaConfigured(false);
                userEntity.setTwoFaKey(null);
            }
            userEntity.setIs2FaEnabled(userInfo.getIs2FaEnabled());
            activityMessage.append("2FA status:" + userInfo.getIs2FaEnabled() + "\n");
        }
        if (!ValidatorUtil.isNull(userInfo.getPassword())) {
            userEntity.setPassword(StringUtil.encodeString(userInfo.getPassword()));
            activityMessage.append("password" + "\n");
        }
        userEntity.setUpdatedDate(new Date());
    }

    /**
     * To reset pwd user entity.
     *
     * @param userEntity the user entity
     * @param randomPassword the random password
     * @return the user entity
     */
    public static UserEntity toResetPwdUserEntity(final UserEntity userEntity, final String randomPassword) {
        userEntity.setPassword(StringUtil.encodeString(randomPassword));
        userEntity.setUpdatedDate(new Date());
        return userEntity;
    }
}
