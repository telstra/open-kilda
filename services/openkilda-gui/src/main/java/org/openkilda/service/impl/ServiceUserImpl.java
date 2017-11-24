package org.openkilda.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openkilda.dao.UserRepository;
import org.openkilda.entity.User;
import org.openkilda.service.ServiceBase;
import org.openkilda.service.ServiceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


/**
 * The Class ServiceUserImpl.
 *
 * @author Gaurav Chugh
 */

@Service(value = "serviceUser")
public class ServiceUserImpl extends ServiceBase implements UserDetailsService, ServiceUser {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ServiceUserImpl.class);


    /** The user repository. */
    @Autowired
    UserRepository userRepository;


    /*
     * (non-Javadoc)
     * 
     * @see com.telstra.service.ServiceUser#addNewUser(com.telstra.entity.User)
     */
    @Override
    public User addNewUser(User user) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.telstra.service.ServiceUser#getAllUsers(boolean)
     */
    @Override
    public List<User> getAllUsers(boolean activeFlag) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.telstra.service.ServiceUser#updateUser(com.telstra.entity.User)
     */
    @Override
    public User updateUser(User user) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.security.core.userdetails.UserDetailsService#loadUserByUsername(java.
     * lang.String)
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String password = "";
        final User user = userRepository.findByUsername(username);
        // TODO: keeping empty authorities as of now
        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>(0);
        if (user == null) {
            throw new UsernameNotFoundException(username);
        } else {
            password = user.getPassword();
        }
        return new org.springframework.security.core.userdetails.User(username, password,
                authorities);
    }

}
