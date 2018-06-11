package org.usermanagement.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.openkilda.constants.Status;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;

@Component
public class UserValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserValidator.class);

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private UserRepository userRepository;

    public void validateCreateUser(final UserInfo userInfo) {
        if (ValidatorUtil.isNull(userInfo.getName())) {
            LOGGER.error("Validation fail for user(username: " + userInfo.getUsername()
                    + "). Error: " + messageUtil.getAttributeNotNull("name"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        } else if (ValidatorUtil.isNull(userInfo.getUsername())) {
            LOGGER.error("Validation fail for user(name: " + userInfo.getName() + "). Error: "
                    + messageUtil.getAttributeNotNull("username"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("username"));
        } else if (ValidatorUtil.isNull(userInfo.getEmail())) {
            LOGGER.error("Validation fail for user(username: " + userInfo.getUsername()
                    + "). Error: " + messageUtil.getAttributeNotNull("email"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("email"));
        } else if (ValidatorUtil.isNull(userInfo.getRoleIds())) {
            LOGGER.error("Validation fail for user(username: " + userInfo.getUsername()
                    + "). Error: " + messageUtil.getAttributeNotNull("role"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("role"));
        }

        UserEntity userEntityTemp = userRepository.findByUsername(userInfo.getUsername());
        if (userEntityTemp != null) {
            LOGGER.error("Validation fail for user(username: " + userInfo.getUsername()
                    + "). Error: " + messageUtil.getAttributeUnique("username"));
            throw new RequestValidationException(messageUtil.getAttributeUnique("username"));
        }
    }

    public UserEntity validateUpdateUser(final UserInfo userInfo) {
        UserEntity userEntity = validateUserId(userInfo.getUserId());

        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.error("Validation fail for update user request(id: " + userInfo.getUserId()
                + "). Error: " + messageUtil.getAttributeNotNull("user"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("user"));
        }

        if (ValidatorUtil.isNull(userInfo.getName()) && ValidatorUtil.isNull(userInfo.getRoleIds())
                && ValidatorUtil.isNull(userInfo.getStatus())) {
            LOGGER.error("Validation fail for update user request(id: " + userInfo.getUserId()
                + "). Error: " + messageUtil.getAttributeNotNull("name, status and role_id"));
            throw new RequestValidationException(
                    messageUtil.getAttributeNotNull("name, status and role_id"));
        }

        if (!ValidatorUtil.isNull(userInfo.getStatus())
                && Status.getStatusByName(userInfo.getStatus()) == null) {
            LOGGER.error("Validation fail for update user request(id: " + userInfo.getUserId()
                + "). Error: " + messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
        }


        UserEntity userEntityTemp = userRepository.findByUsername(userInfo.getUsername());
        if (userEntityTemp != null && !userEntityTemp.getUserId().equals(userInfo.getUserId())) {
            LOGGER.error("Validation fail for update user request(id: " + userInfo.getUserId()
                + "). Error: " + messageUtil.getAttributeUnique("username"));
            throw new RequestValidationException(messageUtil.getAttributeUnique("username"));
        }
        return userEntity;
    }

    public UserEntity validateUserId(final long userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);

        if (ValidatorUtil.isNull(userEntity) || userId == 1) {
            LOGGER.error("Validation failed for user (id: " + userId
                + "). Error: " + messageUtil.getAttributeInvalid("user_id", userId + ""));
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("user_id", userId + ""));
        }
        return userEntity;
    }

    public void validateChangePassword(final UserInfo userInfo) {
        if (ValidatorUtil.isNull(userInfo.getNewPassword())) {
            LOGGER.error("Validation fail for change user password request (id: " + userInfo.getUserId()
                + "). Error: " + messageUtil.getAttributeNotNull("password"));
            throw new RequestValidationException(messageUtil.getAttributeNotNull("password"));
        }
    }
}
