package org.usermanagement.validator;

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

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private UserRepository userRepository;

    public void validateCreateUser(final UserInfo userInfo) {
        UserEntity userEntityTemp = userRepository.findByUsername(userInfo.getUsername());
        if (ValidatorUtil.isNull(userInfo.getName())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name"));
        } else if (ValidatorUtil.isNull(userInfo.getUsername())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("username"));
        } else if (ValidatorUtil.isNull(userInfo.getEmail())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("email"));
        } else if (ValidatorUtil.isNull(userInfo.getRoleIds())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("role"));
        } else if (userEntityTemp != null) {
            throw new RequestValidationException(messageUtil.getAttributeUnique("username"));
        }
    }

    public void validateUpdateUser(final UserInfo userInfo) {
        UserEntity userEntity = userRepository.findByUserId(userInfo.getUserId());
        UserEntity userEntityTemp = userRepository.findByUsername(userInfo.getUsername());

        if (ValidatorUtil.isNull(userEntity)) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("user"));
        }

        if (ValidatorUtil.isNull(userInfo.getName()) && ValidatorUtil.isNull(userInfo.getRoleIds())
                && ValidatorUtil.isNull(userInfo.getStatus())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("name, status and role_id"));
        }

        if(!ValidatorUtil.isNull(userInfo.getStatus()) && Status.getStatusByName(userInfo.getStatus()) == null) {
            throw new RequestValidationException(messageUtil.getAttributeInvalid("status", userInfo.getStatus()));
        }

        if (userEntityTemp != null && !userEntityTemp.getUserId().equals(userInfo.getUserId())) {
            throw new RequestValidationException(messageUtil.getAttributeUnique("username"));
        }
    }

    public void validateChangePassword(final UserInfo userInfo) {

        if (ValidatorUtil.isNull(userInfo.getNewPassword())) {
            throw new RequestValidationException(messageUtil.getAttributeNotNull("password"));
        }

    }
}
