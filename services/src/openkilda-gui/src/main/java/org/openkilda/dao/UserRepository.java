package org.openkilda.dao;

import java.util.List;

import org.openkilda.entity.User;
import org.springframework.stereotype.Repository;

/**
 * The Interface UserRepository.
 *
 * @author Gaurav Chugh
 */
@Repository
public interface UserRepository extends GenericRepository<User, Long> {

    /**
     * Find by username.
     *
     * @param userName the user name
     * @return the user
     */
    User findByUsername(String userName);

    /**
     * Find by active flag.
     *
     * @param activeFlag the active flag
     * @return the list
     */
    List<User> findByActiveFlag(boolean activeFlag);

}
