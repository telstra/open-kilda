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

import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.OtpRequiredException;
import org.openkilda.exception.TwoFaKeyNotSetException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;

import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;

public class CustomAuthenticationProvider extends DaoAuthenticationProvider {

    @Autowired
    private UserRepository userRepository;

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
            throw new BadCredentialsException("Invalid username or password");
        }

        Authentication result = super.authenticate(auth);

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
