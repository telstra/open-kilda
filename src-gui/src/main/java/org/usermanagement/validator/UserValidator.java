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

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
public class UserValidator {

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
            log.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeNotNull("name"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        } else if (ValidatorUtil.isNull(userInfo.getUsername())) {
            log.warn("Validation fail for user(name: " + userInfo.getName() + "). Error: "
                    + messageUtil.getAttributeNotNull("username"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("username"));
        } else if (ValidatorUtil.isNull(userInfo.getEmail())) {
            log.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeNotNull("email"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("email"));
        } else if (ValidatorUtil.isNull(userInfo.getRoleIds())) {
            log.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
                    + messageUtil.getAttributeNotNull("role"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("role"));
        } else if (userInfo.getName().length() > 255) {
            throw new RequestValidationException(messageUtil.getAttributeLengthInvalid("Name"));
        } else if (userInfo.getEmail().length() > 255) {
            throw new RequestValidationException(messageUtil.getAttributeLengthInvalid("Email"));
        } else if (userInfo.getUsername().length() > 255) {
            throw new RequestValidationException(messageUtil.getAttributeLengthInvalid("Username"));
        }

        UserEntity userEntityTemp = userRepository.findByUsernameIgnoreCase(userInfo.getUsername());
        if (userEntityTemp != null) {
            log.warn("Validation fail for user(username: " + userInfo.getUsername() + "). Error: "
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
            log.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeNotNull("user"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("user"));
        }

        if (ValidatorUtil.isNull(userInfo.getName()) && ValidatorUtil.isNull(userInfo.getRoleIds())
                && ValidatorUtil.isNull(userInfo.getStatus()) && ValidatorUtil.isNull(userInfo.getIs2FaEnabled())) {
            log.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeNotNull("name, 2FA, status and role_id"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name, 2FA, status and role_id"));
        }

        if (!ValidatorUtil.isNull(userInfo.getStatus()) && Status.getStatusByName(userInfo.getStatus()) == null) {
            log.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
        } else if (!ValidatorUtil.isNull(userInfo.getName())) {
            if (userInfo.getName().length() > 255) {
                throw new RequestValidationException(messageUtil.getAttributeLengthInvalid("Name"));
            }
        }
        if (!ValidatorUtil.isNull(userInfo.getUsername())) {
            UserEntity userEntityTemp = userRepository.findByUsernameIgnoreCase(userInfo.getUsername());
            if (userEntityTemp != null && !userEntityTemp.getUserId().equals(userInfo.getUserId())) {
                log.warn("Validation fail for update user request(id: " + userInfo.getUserId() + "). Error: "
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
            log.warn("Validation failed for user (id: " + userId + "). Error: "
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
            log.warn("Validation fail for change user password request (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeNotNull("password"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("password"));
        }

        Matcher matcherOne = Pattern.compile(regexOne).matcher(userInfo.getNewPassword());
        if (!matcherOne.matches()) {
            log.warn("Validation fail for change user password request (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordMustContain());
            throw new RequestValidationException(messageUtil.getAttributePasswordMustContain());
        }

        Matcher matcherTwo = Pattern.compile(regexTwo).matcher(userInfo.getNewPassword());
        if (matcherTwo.find()) {
            log.warn("Validation fail for change user password request (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordMustNotContain());
            throw new RequestValidationException(messageUtil.getAttributePasswordMustNotContain());
        }

        if (userInfo.getNewPassword().equals(userInfo.getPassword())) {
            log.warn("New password not valid (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordShouldNotSame());
            throw new RequestValidationException(messageUtil.getAttributePasswordShouldNotSame());
        }

        if (userInfo.getNewPassword().equals(userInfo.getPassword())) {
            log.warn("New password not valid (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributePasswordLength("8", "15"));
            throw new RequestValidationException(messageUtil.getAttributePasswordLength("8", "15"));
        }
    }
}
