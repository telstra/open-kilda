package org.openkilda.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;

import org.openkilda.exception.InvalidOtpException;
import org.openkilda.exception.OtpRequiredException;
import org.openkilda.exception.TwoFaKeyNotSetException;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.dao.repository.UserRepository;

public class CustomAuthenticationProvider extends DaoAuthenticationProvider {

    @Autowired
    private UserRepository userRepository;


    /* (non-Javadoc)
     * @see org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider#authenticate(org.springframework.security.core.Authentication)
     */
    @Override
    public Authentication authenticate(final Authentication auth)
            throws org.springframework.security.core.AuthenticationException {
        CustomWebAuthenticationDetails customWebAuthenticationDetails =
                ((CustomWebAuthenticationDetails) auth.getDetails());
        String verificationCode = customWebAuthenticationDetails.getVerificationCode();
        UserEntity user = userRepository.findByUsername(auth.getName());
        if (user == null || !user.getActiveFlag()) {
            throw new BadCredentialsException("Invalid username or password");
        }

        Authentication result = super.authenticate(auth);

        if (user.getIs2FaEnabled()) {
            if(!user.getIs2FaConfigured() && !customWebAuthenticationDetails.isConfigure2Fa()) {
                throw new TwoFaKeyNotSetException();
            } else {
                if(verificationCode == null  || verificationCode.isEmpty()) {
                    throw new OtpRequiredException();
                } else if (!TwoFactorUtility.validateOtp(verificationCode, user.getTwoFaKey())) {
                    throw new InvalidOtpException("Invalid verfication code");
                }
            }
        }
        return new UsernamePasswordAuthenticationToken(user, result.getCredentials(),
                result.getAuthorities());
    }

    /* (non-Javadoc)
     * @see org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider#supports(java.lang.Class)
     */
    @Override
    public boolean supports(final Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
