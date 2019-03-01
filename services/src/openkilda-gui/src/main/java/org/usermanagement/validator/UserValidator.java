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

package org.usermanagement.validator;

import org.openkilda.constants.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Class UserValidator.
 */

@Component
public class UserValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserValidator.class);

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private UserRepository userRepository;

    /**
     * Validate create user.
     *
     * @param userInfo the user info
     */
    public void validateCreateUser(final UserInfo userInfo) {
        if (ValidatorUtil.isNull(userInfo.getName())) {
            LOGGER.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeNotNull("name"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        } else if (ValidatorUtil.isNull(userInfo.getUsername())) {
            LOGGER.warn("Validation fail for user(name: " + userInfo.getName() + "). Error: "
                    + messageUtil.getAttributeNotNull("username"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("username"));
        } else if (ValidatorUtil.isNull(userInfo.getEmail())) {
            LOGGER.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeNotNull("email"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("email"));
        } else if (ValidatorUtil.isNull(userInfo.getRoleIds())) {
            LOGGER.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeNotNull("role"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("role"));
        }

        UserEntity userEntityTemp = userRepository.findByUsernameIgnoreCase(userInfo.getUsername());
        if (userEntityTemp != null) {
            LOGGER.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeUnique("username"));
            throw new RequestValidationException(messageUtil.getAttributeUnique("username"));
        }
    }

    /**
     * Validate update user.
     *
     * @param userInfo the user info
     * @return the user entity
     */
    public UserEntity validateUpdateUser(final UserInfo userInfo) {
        UserEntity userEntity = validateUserId(userInfo.getUserId());

        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeNotNull("user"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("user"));
        }

        if (ValidatorUtil.isNull(userInfo.getName()) && ValidatorUtil.isNull(userInfo.getRoleIds())
                && ValidatorUtil.isNull(userInfo.getStatus()) && ValidatorUtil.isNull(userInfo.getIs2FaEnabled())) {
            LOGGER.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeNotNull("name, 2FA, status and role_id"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name, 2FA, status and role_id"));
        }

        if (!ValidatorUtil.isNull(userInfo.getStatus()) && Status.getStatusByName(userInfo.getStatus()) == null) {
            LOGGER.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
        }

        if (!ValidatorUtil.isNull(userInfo.getUsername())) {
            UserEntity userEntityTemp = userRepository.findByUsernameIgnoreCase(userInfo.getUsername());
            if (userEntityTemp != null && !userEntityTemp.getUserId().equals(userInfo.getUserId())) {
                LOGGER.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                        + messageUtil.getAttributeUnique("username"));
                throw new RequestValidationException(messageUtil.getAttributeUnique("username"));
            }
        }
        return userEntity;
    }

    /**
     * Validate user id.
     *
     * @param userId the user id
     * @return the user entity
     */
    public UserEntity validateUserId(final long userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);

        if (ValidatorUtil.isNull(userEntity) || userId == 1) {
            LOGGER.warn("Validation failed for user (id: " + userId + "). Error: "
                    + messageUtil.getAttributeInvalid("user_id", userId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }
        return userEntity;
    }

    /**
     * Validate change password.
     *
     * @param userInfo the user info
     */
    public void validateChangePassword(final UserInfo userInfo) {
        String regexOne = "^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[^\\w\\s]).{8,15}$";
        String regexTwo = "[%,&,+,\\,\\s,\"]";

        if (ValidatorUtil.isNull(userInfo.getNewPassword())) {
            LOGGER.warn("Validation fail for change user password request (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeNotNull("password"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("password"));
        }

        Matcher matcherOne = Pattern.compile(regexOne).matcher(userInfo.getNewPassword());
        if (!matcherOne.matches()) {
            LOGGER.warn("Validation fail for change user password request (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordMustContain());
            throw new RequestValidationException(messageUtil.getAttributePasswordMustContain());
        }

        Matcher matcherTwo = Pattern.compile(regexTwo).matcher(userInfo.getNewPassword());
        if (matcherTwo.find()) {
            LOGGER.warn("Validation fail for change user password request (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordMustNotContain());
            throw new RequestValidationException(messageUtil.getAttributePasswordMustNotContain());
        }

        if (userInfo.getNewPassword().equals(userInfo.getPassword())) {
            LOGGER.warn("New password not valid (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordShouldNotSame());
            throw new RequestValidationException(messageUtil.getAttributePasswordShouldNotSame());
        }

        if (userInfo.getNewPassword().equals(userInfo.getPassword())) {
            LOGGER.warn("New password not valid (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordLength("8", "15"));
            throw new RequestValidationException(messageUtil.getAttributePasswordLength("8", "15"));
        }
    }
}
