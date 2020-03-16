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

package org.usermanagement.service;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.constants.IConstants;
import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.OtpRequiredException;
import org.openkilda.exception.TwoFaKeyNotSetException;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.security.TwoFactorUtility;
import org.openkilda.utility.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.usermanagement.conversion.RoleConversionUtil;
import org.usermanagement.conversion.UserConversionUtil;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.entity.UserSettingEntity;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.dao.repository.UserSettingRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.MailUtils;
import org.usermanagement.util.MessageCodeUtil;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;
import org.usermanagement.validator.UserValidator;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Class ServiceUserImpl.
 *
 * @author Gaurav Chugh
 */

@Service
public class UserService implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MessageUtils messageUtil;

    @Autowired
    private UserValidator userValidator;

    @Autowired
    private MailService mailService;

    @Autowired
    private MailUtils mailUtils;

    @Autowired
    private UserSettingRepository userSettingRepository;

    @Autowired
    private ActivityLogger activityLogger;

    @Autowired
    MessageCodeUtil messageCodeUtil;
    
    @Autowired
    private ServerContext serverContext;

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.core.userdetails.UserDetailsService#
     * loadUserByUsername(java. lang.String)
     */
    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsernameIgnoreCase(username);

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>(0);
        if (user == null) {
            LOGGER.warn("User with username '" + username + "' not found.");
            throw new UsernameNotFoundException(username);
        }

        return new org.springframework.security.core.userdetails.User(username, user.getPassword(), authorities);
    }

    /**
     * Creates the user.
     *
     * @param userRequest the user request
     * @return the user info
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo createUser(final UserInfo userRequest) {
        userValidator.validateCreateUser(userRequest);

        Set<RoleEntity> roleEntities = roleService.getRolesById(userRequest.getRoleIds());

        UserEntity userEntity = UserConversionUtil.toUserEntity(userRequest, roleEntities);
        String password = ValidatorUtil.randomAlphaNumeric(16);
        userEntity.setPassword(StringUtil.encodeString(password));
        userEntity.setIs2FaEnabled(userRequest.getIs2FaEnabled());
        userEntity = userRepository.save(userEntity);
        LOGGER.info("User with username '" + userEntity.getUsername() + "' created successfully.");

        activityLogger.log(ActivityType.CREATE_USER, userRequest.getUsername());

        try {
            if (userEntity.getUserId() != null) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("name", userEntity.getName());
                map.put("username", userEntity.getUsername());
                map.put("password", password);
                mailService.send(userEntity.getEmail(), mailUtils.getSubjectAccountUsername(),
                        TemplateService.Template.ACCOUNT_USERNAME, map);
                mailService.send(userEntity.getEmail(), mailUtils.getSubjectAccountPassword(),
                        TemplateService.Template.ACCOUNT_PASSWORD, map);
                LOGGER.info("Username and password email sent successfully to user(username: "
                        + userEntity.getUsername() + ").");
            }
        } catch (Exception ex) {
            LOGGER.warn("User registration email failed for username:'" + userEntity.getUsername());
        }
        return UserConversionUtil.toUserInfo(userEntity);
    }

    /**
     * Update user.
     *
     * @param userInfo the user info
     * @param userId the user id
     * @return the user info
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo updateUser(final UserInfo userInfo, final Long userId) {
        UserEntity userEntity = userValidator.validateUpdateUser(userInfo);
        StringBuilder activityMessage = new StringBuilder(userEntity.getUsername() + " updated with:\n");
        if (userInfo.getRoleIds() != null) {
            StringBuilder roles = new StringBuilder();
            userEntity.getRoles().clear();
            Set<RoleEntity> roleEntities = roleService.getRolesById(userInfo.getRoleIds());
            userEntity.getRoles().addAll(roleEntities);
            for (RoleEntity role : roleEntities) {
                roles = roles.length() > 0 ? roles.append("," + role.getName()) : roles.append(role.getName());
            }
            activityMessage.append("roles:" + roles.toString() + "\n");
        }

        UserConversionUtil.toUpateUserEntity(userInfo, userEntity, activityMessage);
        userEntity = userRepository.save(userEntity);

        activityLogger.log(ActivityType.UPDATE_USER, activityMessage.toString());
        LOGGER.info("User updated successfully (id: " + userId + ")");

        return UserConversionUtil.toUserInfo(userEntity);
    }

    /**
     * Gets the all users.
     *
     * @return the all users
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<UserInfo> getAllUsers() {
        List<UserEntity> userEntities = userRepository.findAll();
        return UserConversionUtil.toAllUsers(userEntities);
    }

    /**
     * Gets the user by id.
     *
     * @param userId the user id
     * @return the user by id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo getUserById(final Long userId) {
        UserEntity userEntity = userValidator.validateUserId(userId);
        return UserConversionUtil.toUserInfo(userEntity);
    }

    /**
     * Gets the user by username.
     *
     * @param userName the user name
     * @return the user by username
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public UserEntity getUserByUsername(final String userName) {
        return userRepository.findByUsernameIgnoreCase(userName);
    }

    /**
     * Update user 2 FA key.
     *
     * @param userName the user name
     * @param secretKey the secret key
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void updateUser2FaKey(final String userName, final String secretKey) {
        UserEntity userEntity = userRepository.findByUsernameIgnoreCase(userName);
        userEntity.setTwoFaKey(secretKey);
        userRepository.save(userEntity);
        LOGGER.info("User 2FA updated successfully (username: " + userName + ")");
    }

    /**
     * Update login detail.
     *
     * @param userName the user name
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void updateLoginDetail(final String userName) {
        UserEntity userEntity = userRepository.findByUsernameIgnoreCase(userName);
        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("User with username '" + userName + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("username", userName + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("username", userName + ""));
        }
        userEntity.setLoginTime(Calendar.getInstance().getTime());
        if (userEntity.getIs2FaEnabled()) {
            userEntity.setIs2FaConfigured(true);
        }
        userRepository.save(userEntity);
        LOGGER.info("User last login updated successfully (username: " + userName + ")");
    }

    /**
     * Delete user by id.
     *
     * @param userId the user id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void deleteUserById(final Long userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);
        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("User with user id '" + userId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("user_id", userId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        userRepository.delete(userEntity);

        activityLogger.log(ActivityType.DELETE_USER, userEntity.getUsername());
        LOGGER.info("User deleted successfully (userId: " + userId + ")");
    }

    /**
     * Assign user by role id.
     *
     * @param roleId the role id
     * @param role the role
     * @return the role
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role assignUserByRoleId(final Long roleId, final Role role) {

        RoleEntity roleEntity = roleRepository.findByRoleId(roleId);
        if (ValidatorUtil.isNull(roleEntity)) {
            LOGGER.warn("Role with role id '" + roleId + "' not found. Error: "
                    + messageUtil.getAttributeInvalid("role_id", roleId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("role_id", roleId + ""));
        }
        roleEntity.getUsers().clear();
        if (role.getUserInfo() != null) {
            for (UserInfo user : role.getUserInfo()) {
                UserEntity userEntity = userRepository.findByUserId(user.getUserId());
                roleEntity.getUsers().add(userEntity);
            }
        }
        roleEntity = roleRepository.save(roleEntity);

        activityLogger.log(ActivityType.ASSIGN_USERS_BY_ROLE, roleEntity.getName());
        LOGGER.info("Users assigned with role successfully (role id: " + roleId + ")");
        return RoleConversionUtil.toRole(roleEntity, false, true);
    }

    /**
     * Change password.
     *
     * @param userInfo the user info
     * @param userId the user id
     * @return the user info
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo changePassword(final UserInfo userInfo, final Long userId) {
        userValidator.validateChangePassword(userInfo);

        UserEntity userEntity = userRepository.findByUserId(userId);
        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("User Entity not found for user(id: " + userId + ")");
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        if (!StringUtil.matches(userInfo.getPassword(), userEntity.getPassword())) {
            LOGGER.warn("Password not matched for user (id: " + userId + "). Error: "
                    + messageUtil.getAttributePasswordInvalid());
            throw new RequestValidationException(messageUtil.getAttributePasswordInvalid());
        }

        if (userEntity.getIs2FaEnabled()) {
            if (!userEntity.getIs2FaConfigured()) {
                LOGGER.warn("2FA key is not configured for user(id: " + userId + "). Error: "
                        + messageUtil.getAttribute2faNotConfiured());
                throw new TwoFaKeyNotSetException(messageUtil.getAttribute2faNotConfiured());
            } else {
                if (userInfo.getCode() == null || userInfo.getCode().isEmpty()) {
                    LOGGER.warn("OTP code is madatory as 2FA is configured for user (id: " + userId + "). Error: "
                            + messageUtil.getAttributeNotNull("OTP"));
                    throw new OtpRequiredException(messageUtil.getAttributeNotNull("OTP"));
                } else if (!TwoFactorUtility.validateOtp(userInfo.getCode(), userEntity.getTwoFaKey())) {
                    LOGGER.warn("Invalid OTP for user (id: " + userId + "). Error: "
                            + messageUtil.getAttributeNotvalid("OTP"));
                    throw new InvalidOtpException(messageUtil.getAttributeNotvalid("OTP"));
                }
            }
        }

        userEntity.setPassword(StringUtil.encodeString(userInfo.getNewPassword()));
        userEntity.setUpdatedDate(new Date());
        userEntity = userRepository.save(userEntity);

        activityLogger.log(ActivityType.CHANGE_PASSWORD, userEntity.getUsername());
        LOGGER.info("User(userId: " + userId + ") password changed successfully.");

        Map<String, Object> context = new HashMap<>();
        context.put("name", userEntity.getName());
        mailService.send(userEntity.getEmail(), mailUtils.getSubjectChangePassword(),
                TemplateService.Template.CHANGE_PASSWORD, context);
        LOGGER.info("Changed password mail sent successfully for user(userId: " + userId + ").");

        return UserConversionUtil.toUserInfo(userEntity);
    }

    /**
     * Reset password.
     *
     * @param userId the user id
     * @param adminFlag the admin flag
     * @return the user info
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo resetPassword(final long userId, final boolean adminFlag) {
        UserInfo userinfo = new UserInfo();
        userinfo.setUserId(userId);

        UserEntity userEntity = userRepository.findByUserId(userId);

        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("User Entity not found for user(id: " + userId + ")");
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }
        String randomPassword = ValidatorUtil.randomAlphaNumeric(16);
        userEntity = UserConversionUtil.toResetPwdUserEntity(userEntity, randomPassword);
        if (adminFlag) {
            userEntity.setIs2FaConfigured(false);
            userEntity.setTwoFaKey(null);
        }
        userEntity = userRepository.save(userEntity);
        if (adminFlag) {
            activityLogger.log(ActivityType.ADMIN_RESET_PASSWORD, userEntity.getUsername());
        } else {
            activityLogger.log(ActivityType.RESET_PASSWORD, userEntity.getUsername());
        }

        LOGGER.info("Password reset successfully for user(userId: " + userId + ").");
        if (!adminFlag) {
            Map<String, Object> context = new HashMap<>();
            context.put("name", userEntity.getName());
            context.put("password", randomPassword);
            mailService.send(userEntity.getEmail(), mailUtils.getSubjectResetPassword(),
                    TemplateService.Template.RESET_ACCOUNT_PASSWORD, context);
            LOGGER.info("Reset password mail sent successfully for user(userId: " + userId + ").");
        }
        userinfo.setPassword(randomPassword);
        return userinfo;
    }

    /**
     * Reset 2 fa.
     *
     * @param userId the user id
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void reset2fa(final long userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);

        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("User Entity not found for user(user_id: " + userId + ")");
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }
        userEntity.setIs2FaConfigured(false);
        userEntity.setTwoFaKey(null);
        userEntity = userRepository.save(userEntity);

        activityLogger.log(ActivityType.RESET_2FA, userEntity.getUsername());
        LOGGER.info("2FA reset successfully for user(user_id: " + userId + ").");
        if (!userEntity.getIs2FaConfigured()) {
            Map<String, Object> context = new HashMap<>();
            context.put("name", userEntity.getName());

            mailService.send(userEntity.getEmail(), mailUtils.getSubjectReset2fa(), TemplateService.Template.RESET_2FA,
                    context);
            LOGGER.info("Reset 2FA mail sent successfully for user(user_id: " + userId + ").");
        }
    }

    /**
     * Save or update settings.
     *
     * @param userInfo the user info
     * @return the user info
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo saveOrUpdateSettings(UserInfo userInfo) {

        if (ValidatorUtil.isNull(userInfo.getUserId())) {
            LOGGER.warn("Validation failed for user (id: " + userInfo.getUserId() + "). Error: "
                    + messageUtil.getAttributeInvalid("user_id", userInfo.getUserId() + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userInfo.getUserId() + ""));
        }

        UserSettingEntity userSettingEntity = userSettingRepository.findOneByUserId(userInfo.getUserId());
        if (userSettingEntity == null) {
            userSettingEntity = new UserSettingEntity();
            userSettingEntity.setUserId(userInfo.getUserId());
        }
        userSettingEntity.setSettings(IConstants.Settings.TOPOLOGY_SETTING);
        userSettingEntity.setData(userInfo.getData());
        userSettingEntity = userSettingRepository.save(userSettingEntity);

        // activityLogger.log(ActivityType.UPDATE_USER_SETTINGS,
        // userInfo.getUserId() + "");
        LOGGER.info("User Settings saved successfully for user(user_id: " + userInfo.getUserId() + ").");
        return userInfo;
    }

    /**
     * Gets the user settings.
     *
     * @param userId the user id
     * @return the user settings
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public String getUserSettings(final long userId) {

        if (ValidatorUtil.isNull(userId)) {
            LOGGER.warn("Validation failed for user (id: " + userId + "). Error: "
                    + messageUtil.getAttributeInvalid("user_id", userId + ""));
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        UserSettingEntity userSettingEntity = userSettingRepository.findOneByUserId(userId);
        if (userSettingEntity == null) {
            LOGGER.warn("User settings not found for user(user_id: " + userId + ")"
                    + messageCodeUtil.getAttributeNotFoundCode());
            throw new RequestValidationException(messageCodeUtil.getAttributeNotFoundCode(),
                    messageUtil.getAttributeNotFound("User settings"));
        }
        return userSettingEntity.getData();
    }

    /**
     * Validate OTP.
     *
     * @param userId the user id
     * @param otp the otp
     * @return true, if successful
     */
    public boolean validateOtp(final long userId, final String otp) {
        UserEntity userEntity = userRepository.findByUserId(userId);
        if (ValidatorUtil.isNull(userEntity)) {
            LOGGER.warn("User Entity not found for user(id: " + userId + ")");
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        if (userEntity.getIs2FaEnabled()) {
            if (!userEntity.getIs2FaConfigured()) {
                LOGGER.warn("2FA key is not configured for user(id: " + userId + "). Error: "
                        + messageUtil.getAttribute2faNotConfiured());
                throw new TwoFaKeyNotSetException(messageUtil.getAttribute2faNotConfiured());
            } else {
                if (otp == null || otp.isEmpty()) {
                    LOGGER.warn("OTP code is madatory as 2FA is configured for user (id: " + userId + "). Error: "
                            + messageUtil.getAttributeNotNull("OTP"));
                    throw new OtpRequiredException(messageUtil.getAttributeNotNull("OTP"));
                } else if (!TwoFactorUtility.validateOtp(otp, userEntity.getTwoFaKey())) {
                    LOGGER.warn("Invalid OTP for user (id: " + userId + "). Error: "
                            + messageUtil.getAttributeNotvalid("OTP"));
                    throw new InvalidOtpException(messageUtil.getAttributeNotvalid("OTP"));
                }
            }
        } else {
            LOGGER.warn("2FA is not enabled for user(id: " + userId + "). Error: "
                    + messageUtil.getAttribute2faNotEnabled());
            throw new TwoFaKeyNotSetException(messageUtil.getAttribute2faNotEnabled());
        }
        return true;
    }

    /**
     * Gets the logged in user info.
     *
     * @return the logged in user info
     */
    public UserInfo getLoggedInUserInfo() {
        UserInfo userInfo = new UserInfo();
        RequestContext requestContext = serverContext.getRequestContext();
        userInfo.setUserId(requestContext.getUserId());
        userInfo.setUsername(requestContext.getUserName());
        userInfo.setIs2FaEnabled(requestContext.getIs2FaEnabled());
        userInfo.setStatus(requestContext.getStatus());
        userInfo.setName(requestContext.getFullName());
        userInfo.setPermissions(requestContext.getPermissions());
        return userInfo;
    }
}
