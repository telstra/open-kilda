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
import org.openkilda.security.CustomWebAuthenticationDetails;
import org.openkilda.security.TwoFactorUtility;
import org.openkilda.utility.StringUtil;
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

    /** The user repository. */
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
        LOGGER.info("Inside loadUserByUsername ");
        UserEntity user = userRepository.findByUsername(username);

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>(0);
        if (user == null) {
            throw new UsernameNotFoundException(username);
        }

        return new org.springframework.security.core.userdetails.User(username, user.getPassword(), authorities);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role getUserByRoleId(final Long roleId) {
        RoleEntity roleEntity = roleRepository.findByroleId(roleId);

        Set<UserEntity> userEntityList = userRepository.findByRoles_roleId(roleId);

        return UserConversionUtil.toRoleByUser(userEntityList, roleEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo createUser(final UserInfo userRequest) {
        userValidator.validateCreateUser(userRequest);

        Set<RoleEntity> roleEntities = new HashSet<>();
        List<RoleEntity> roleEntityList = roleRepository.findAll();
        for (Long roleId : userRequest.getRoleIds()) {
            RoleEntity roleEntity = roleEntityList.parallelStream()
                    .filter((entity) -> entity.getRoleId().equals(roleId)).findFirst().orElse(null);

            if (!ValidatorUtil.isNull(roleEntity)) {
                roleEntities.add(roleEntity);
            } else {
                throw new RequestValidationException(messageUtil.getAttributeNotFound("roles"));
            }
        }

        UserEntity userEntity = UserConversionUtil.toUserEntity(userRequest, roleEntities);
        String password = ValidatorUtil.randomAlphaNumeric(16);
        userEntity.setPassword(StringUtil.encodeString(password));
        userEntity.setIs2FaEnabled(true);
        userEntity = userRepository.save(userEntity);

        if(userEntity.getUserId() != null){
	        Map<String, Object> map = new HashMap<String, Object>();
	        map.put("name",userEntity.getName());
	        map.put("username",userEntity.getUsername());
	        map.put("password",password);
	        mailService.send(userEntity.getEmail(), mailUtils.getSubjectAccountUsername(), TemplateService.Template.ACCOUNT_USERNAME, map);
	        mailService.send(userEntity.getEmail(), mailUtils.getSubjectAccountPassword(), TemplateService.Template.ACCOUNT_PASSWORD, map);
        }
        return UserConversionUtil.toUserInfo(userEntity);
    }


	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public UserInfo updateUser(final UserInfo userRequest, final Long userId) {

		UserEntity userEntity = userRepository.findByUserId(userId);

		if (ValidatorUtil.isNull(userEntity) || userId == 1) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
		}
		userValidator.validateUpdateUser(userRequest);

		if (userRequest.getRoleIds() != null) {
			userEntity.getRoles().clear();
			for (Long roleId : userRequest.getRoleIds()) {
				RoleEntity roleEntity = roleRepository.findByroleId(roleId);
				if (roleEntity != null) {
					userEntity.getRoles().add(roleEntity);
				}
			}
		}

		userEntity = UserConversionUtil.toUpateUserEntity(userRequest, userEntity);
		userEntity = userRepository.save(userEntity);

		return UserConversionUtil.toUserInfo(userEntity);
	}


    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<UserInfo> getAllUsers() {
        List<UserEntity> userEntityList = userRepository.findAll();
        List<UserInfo> userList = UserConversionUtil.toAllUserResponse(userEntityList);
        return userList;
    }


    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UserInfo getUserById(final Long userId) {

        UserEntity userEntity = userRepository.findByUserId(userId);
        if (ValidatorUtil.isNull(userEntity) || userId == 1) {
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        return UserConversionUtil.toUserInfo(userEntity);
    }


    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public UserEntity getUserByUsername(final String userName) {
        return userRepository.findByUsername(userName);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void updateUser2FAKey(final String userName, final String secretKey) {
        UserEntity userEntity = userRepository.findByUsername(userName);
        userEntity.setTwoFaKey(secretKey);
        userRepository.save(userEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void updateLoginDetail(final String userName) {
        UserEntity userEntity = userRepository.findByUsername(userName);
        userEntity.setLoginTime(Calendar.getInstance().getTime());
        userEntity.setIs2FaConfigured(true);
        userRepository.save(userEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public void deleteUserById(final Long userId) {

        UserEntity userEntity = userRepository.findByUserId(userId);
        if (ValidatorUtil.isNull(userEntity)) {
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

        userRepository.delete(userEntity);

    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Role assignUserByRoleId(final Long roleId, final Role role) {

        RoleEntity roleEntity = roleRepository.findByroleId(roleId);
        roleEntity.getUsers().clear();
        if (role.getUserInfo() != null) {
	        for (UserInfo user : role.getUserInfo()) {
	            UserEntity userEntity = userRepository.findByUserId(user.getUserId());
	            roleEntity.getUsers().add(userEntity);
	        }
        }
        roleRepository.save(roleEntity);
        
        return UserConversionUtil.toRoleByUser(roleEntity.getUsers(), roleEntity);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public UserInfo changePassword(final UserInfo userRequest, final Long userId,
			final CustomWebAuthenticationDetails customWebAuthenticationDetails) {

        UserEntity userEntity = userRepository.findByUserId(userId);

        if (ValidatorUtil.isNull(userEntity)) {
            throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
        }

		if (userEntity.getIs2FaEnabled()) {
			if (!userEntity.getIs2FaConfigured() && !customWebAuthenticationDetails.isConfigure2Fa()) {
				throw new TwoFaKeyNotSetException(messageUtil.getAttribute2faNotConfiured());
			} else {
				if (userRequest.getCode() == null || userRequest.getCode().isEmpty()) {
					throw new OtpRequiredException(messageUtil.getAttributeNotNull("OTP"));
				} else if (!TwoFactorUtility.validateOtp(userRequest.getCode(), userEntity.getTwoFaKey())) {
					throw new InvalidOtpException(messageUtil.getAttributeInvalid("OTP", userRequest.getCode()));
				}
			}
		}
		
		if (!StringUtil.matches(userRequest.getPassword(), userEntity.getPassword())) {
			throw new RequestValidationException(messageUtil.getAttributePasswordInvalid());
		}
		
        userRequest.setUserId(userId);
        userValidator.validateChangePassword(userRequest);

        userEntity.setPassword(StringUtil.encodeString(userRequest.getNewPassword()));
        userEntity.setUpdatedDate(new Date());

        userEntity = userRepository.save(userEntity);

        Map<String, Object> context = new HashMap<>();
        context.put("name", userEntity.getName());
        mailService.send(userEntity.getEmail(), mailUtils.getSubjectChangePassword(),
                TemplateService.Template.CHANGE_PASSWORD, context);

        return UserConversionUtil.toUserInfo(userEntity);
    }

	@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
	public UserInfo resetPassword(final long userId, final boolean adminFlag) {
		UserInfo userinfo = new UserInfo();
		userinfo.setUserId(userId);

		UserEntity userEntity = userRepository.findByUserId(userId);

		if (ValidatorUtil.isNull(userEntity)) {
			throw new RequestValidationException(messageUtil.getAttributeInvalid("user_id", userId + ""));
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

        if(!userEntity.getIs2FaConfigured()){
            Map<String, Object> context = new HashMap<>();
            context.put("name", userEntity.getName());

	        mailService.send(userEntity.getEmail(), mailUtils.getSubjectReset2fa(),
	                TemplateService.Template.RESET_2FA, context);
        }
    }
}