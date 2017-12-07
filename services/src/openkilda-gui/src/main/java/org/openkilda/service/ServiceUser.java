package org.openkilda.service;

import java.util.List;

import org.openkilda.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * The Interface ServiceUser.
 *
 * @author Gaurav Chugh
 */
public interface ServiceUser extends UserDetailsService {

    /**
     * Adding a new user.
     *
     * @param user is the new new {@link User} that has to be added
     * @return the user
     */
    User addNewUser(User user);

    /**
     * Gets the all users.
     *
     * @param activeFlag the active flag
     * @return the all users
     */
    List<User> getAllUsers(boolean activeFlag);

    /**
     * Update user.
     *
     * @param user the user
     * @return the user
     */
    User updateUser(User user);

}
