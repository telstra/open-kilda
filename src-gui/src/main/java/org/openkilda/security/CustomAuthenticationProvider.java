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

package org.openkilda.security;

import org.openkilda.constants.IConstants.ApplicationSetting;
import org.openkilda.constants.Status;
import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.OtpRequiredException;
import org.openkilda.exception.TwoFaKeyNotSetException;
import org.openkilda.service.ApplicationSettingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

public class CustomAuthenticationProvider extends DaoAuthenticationProvider {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ApplicationSettingService applicationSettingService;

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.security.authentication.dao.
     * AbstractUserDetailsAuthenticationProvider#authenticate(org.
     * springframework.security.core.Authentication)
     */
    @Override
    public Authentication authenticate(final Authentication auth)
            throws org.springframework.security.core.AuthenticationException {
        CustomWebAuthenticationDetails customWebAuthenticationDetails = ((CustomWebAuthenticationDetails) auth
                .getDetails());
        String verificationCode = customWebAuthenticationDetails.getVerificationCode();
        UserEntity user = userRepository.findByUsernameIgnoreCase(auth.getName());
        if (user == null || !user.getActiveFlag()) {
            throw new BadCredentialsException("Login failed; Invalid email or password.");
        }
        String loginCount = null;
        String unlockTime = null;
        if (user.getUserId() != 1) {
            loginCount = applicationSettingService.getApplicationSetting(ApplicationSetting.INVALID_LOGIN_ATTEMPT);
            unlockTime = applicationSettingService.getApplicationSetting(ApplicationSetting
                .USER_ACCOUNT_UNLOCK_TIME);
            if (!user.getStatusEntity().getStatus().equalsIgnoreCase("ACTIVE")) {
                checkUserLoginAttempts(user, loginCount, unlockTime);
            }
        }
        try {
            final Authentication result = super.authenticate(auth);
            if (user.getIs2FaEnabled()) {
                if (!user.getIs2FaConfigured() && !customWebAuthenticationDetails.isConfigure2Fa()) {
                    throw new TwoFaKeyNotSetException();
                } else {
                    if (verificationCode == null || verificationCode.isEmpty()) {
                        throw new OtpRequiredException();
                    } else if (!TwoFactorUtility.validateOtp(verificationCode, user.getTwoFaKey())) {
                        throw new InvalidOtpException("Invalid verfication code");
                    }
                }
            }
            return new UsernamePasswordAuthenticationToken(user, result.getCredentials(), result.getAuthorities());
        } catch (BadCredentialsException e) {
            if (user.getUserId() != 1) {
                updateInvalidLoginAttempts(user, loginCount, unlockTime);
            }
            throw new BadCredentialsException(e.getMessage());
        }
    
    }

    private void checkUserLoginAttempts(UserEntity user, String value, String accUnlockTime) {
        if (user.getFailedLoginCount() != null) {
            Date loginTime = user.getFailedLoginTime();
            Calendar cal = Calendar.getInstance();
            cal.setTime(loginTime);
            cal.add(Calendar.MINUTE, Integer.valueOf(user.getUnlockTime()));
            Date time = Calendar.getInstance().getTime();
            if (cal.getTime().after(time)) {
                Date calTime = cal.getTime();
                long unlockTime = calTime.getTime() - time.getTime();  
                long diffMinutes = unlockTime / (60 * 1000);
                long minutes = diffMinutes + 1;
                throw new LockedException("User account is locked, will be unlocked after " 
                        + minutes + " minute(s)");
            } else {
                user.setStatusEntity(Status.ACTIVE.getStatusEntity());
                user.setFailedLoginCount(null);
                user.setUnlockTime(null);
                user.setLoginTime(new Timestamp(System.currentTimeMillis()));
                userRepository.save(user);
            }
        }
    }

    private void updateInvalidLoginAttempts(UserEntity entity, String value, String accUnlockTime) {
        Integer loginCount = entity.getFailedLoginCount();
        if (loginCount != null) {
            if (loginCount + 1 >= Integer.valueOf(value)) {
                entity.setFailedLoginCount(loginCount + 1);
                entity.setUnlockTime(Integer.valueOf(accUnlockTime));
                entity.setFailedLoginTime(new Timestamp(System.currentTimeMillis()));
                entity.setStatusEntity(Status.getStatusByCode(Status.LOCK.getCode()).getStatusEntity());
                userRepository.save(entity);
                throw new LockedException("User account is locked for "
                        + Integer.valueOf(accUnlockTime) + " minute(s)");
            }
            entity.setFailedLoginCount(loginCount + 1);
        } else {
            entity.setFailedLoginCount(1);
        }
        userRepository.save(entity);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.security.authentication.dao.
     * AbstractUserDetailsAuthenticationProvider#supports(java.lang.Class)
     */
    @Override
    public boolean supports(final Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
