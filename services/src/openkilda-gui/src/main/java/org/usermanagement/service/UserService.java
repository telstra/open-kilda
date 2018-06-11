package org.usermanagement.service;

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

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.OtpRequiredException;
import org.openkilda.exception.TwoFaKeyNotSetException;
import org.openkilda.security.TwoFactorUtility;
import org.openkilda.utility.StringUtil;
import org.usermanagement.conversion.RoleConversionUtil;
import org.usermanagement.conversion.UserConversionUtil;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.RoleRepository;
import org.usermanagement.dao.repository.UserRepository;
import org.usermanagement.exception.RequestValidationException;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.MailUtils;
import org.usermanagement.util.MessageUtils;
import org.usermanagement.util.ValidatorUtil;
import org.usermanagement.validator.UserValidator;

/**
 * The Class ServiceUserImpl.
 *
 * @author Gaurav Chugh
 */
@Service
public class UserService implements UserDetailsService {

    /** The Constant LOG. */
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

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.core.userdetails.UserDetailsService#
     * loadUserByUsername(java. lang.String)
     */
    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username);

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>(0);
        if (user == null) {
            LOGGER.error("User with username '" + username + "' not found.");
            throw new UsernameNotFoundException(username);
        }

        return new org.springframework.security.core.userdetails.User(username, user.getPassword(),
                authorities);
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
        userEntity.setIs2FaEnabled(true);
        userEntity = userRepository.save(userEntity);
        LOGGER.info("User with username '" + userEntity.getUsername() + "' created successfully.");

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

        if (userInfo.getRoleIds() != null) {
            userEntity.getRoles().clear();
            Set<RoleEntity> roleEntities = roleService.getRolesById(userInfo.getRoleIds());
            userEntity.getRoles().addAll(roleEntities);
        }

        UserConversionUtil.toUpateUserEntity(userInfo, userEntity);
        userEntity = userRepository.save(userEntity);
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
        return userRepository.findByUsername(userName);
    }

    /**
     * Update user 2 FA key.
     *
     * @param userName the user name
     * @param secretKey the secret key
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void updateUser2FAKey(final String userName, final String secretKey) {
        UserEntity userEntity = userRepository.findByUsername(userName);
        userEntity.setTwoFaKey(secretKey);
        userRepository.save(userEntity);
    }

    /**
     * Update login detail.
     *
     * @param userName the user name
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void updateLoginDetail(final String userName) {
        UserEntity userEntity = userRepository.findByUsername(userName);
        userEntity.setLoginTime(Calendar.getInstance().getTime());
        userEntity.setIs2FaConfigured(true);
        userRepository.save(userEntity);
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
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        userRepository.delete(userEntity);
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
        roleEntity.getUsers().clear();
        if (role.getUserInfo() != null) {
            for (UserInfo user : role.getUserInfo()) {
                UserEntity userEntity = userRepository.findByUserId(user.getUserId());
                roleEntity.getUsers().add(userEntity);
            }
        }
        roleEntity = roleRepository.save(roleEntity);
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
            LOGGER.error("User Entity not found for user(id: " + userId + ")");
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        if (userEntity.getIs2FaEnabled()) {
            if (!userEntity.getIs2FaConfigured()) {
                LOGGER.error("2FA key is not configured for user(id: " + userId + "). Error: "
                        + messageUtil.getAttribute2faNotConfiured());
                throw new TwoFaKeyNotSetException(messageUtil.getAttribute2faNotConfiured());
            } else {
                if (userInfo.getCode() == null || userInfo.getCode().isEmpty()) {
                    LOGGER.error("OTP code is madatory as 2FA is configured for user (id: " + userId + "). Error: "
                            + messageUtil.getAttributeNotNull("OTP"));
                    throw new OtpRequiredException(messageUtil.getAttributeNotNull("OTP"));
                } else if (!TwoFactorUtility.validateOtp(userInfo.getCode(),
                        userEntity.getTwoFaKey())) {
                    LOGGER.error("Invalid OTP for user (id: " + userId + "). Error: "
                            + messageUtil.getAttributeInvalid("OTP", userInfo.getCode()));
                    throw new InvalidOtpException(
                            messageUtil.getAttributeInvalid("OTP", userInfo.getCode()));
                }
            }
        }

        if (!StringUtil.matches(userInfo.getPassword(), userEntity.getPassword())) {
            LOGGER.error("Password not matched for user (id: " + userId + "). Error: "
                    + messageUtil.getAttributePasswordInvalid());
            throw new RequestValidationException(messageUtil.getAttributePasswordInvalid());
        }

        userEntity.setPassword(StringUtil.encodeString(userInfo.getNewPassword()));
        userEntity.setUpdatedDate(new Date());
        userEntity = userRepository.save(userEntity);
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
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("user_id", userId + ""));
        }
        String randomPassword = ValidatorUtil.randomAlphaNumeric(16);
        userEntity = UserConversionUtil.toResetPwdUserEntity(userEntity, randomPassword);
        if (adminFlag) {
            userEntity.setIs2FaConfigured(false);
            userEntity.setTwoFaKey(null);
        }
        userEntity = userRepository.save(userEntity);

        if (!adminFlag) {
            Map<String, Object> context = new HashMap<>();
            context.put("name", userEntity.getName());
            context.put("password", randomPassword);
            mailService.send(userEntity.getEmail(), mailUtils.getSubjectResetPassword(),
                    TemplateService.Template.RESET_ACCOUNT_PASSWORD, context);
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
            throw new RequestValidationException(
                    messageUtil.getAttributeInvalid("user_id", userId + ""));
        }
        userEntity.setIs2FaConfigured(false);
        userEntity.setTwoFaKey(null);
        userEntity = userRepository.save(userEntity);

        if (!userEntity.getIs2FaConfigured()) {
            Map<String, Object> context = new HashMap<>();
            context.put("name", userEntity.getName());

            mailService.send(userEntity.getEmail(), mailUtils.getSubjectReset2fa(),
                    TemplateService.Template.RESET_2FA, context);
        }
    }
}
