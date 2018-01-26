package org.openkilda.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

import org.openkilda.dao.UserRepository;
import org.openkilda.entity.User;

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

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.core.userdetails.UserDetailsService#
     * loadUserByUsername(java. lang.String)
     */
    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        LOGGER.info("Inside loadUserByUsername ");
        User user = userRepository.findByUsername(username);

        // TODO: keeping empty authorities as of now
        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>(0);
        if (user == null) {
            throw new UsernameNotFoundException(username);
        }

        return new org.springframework.security.core.userdetails.User(username, user.getPassword(),
                authorities);
    }

}
