package org.openkilda.dao;

import java.util.List;

import org.openkilda.entity.User;
import org.springframework.stereotype.Repository;

/**
 * 
 * @author Gaurav Chugh
 *
 */
@Repository
public interface UserRepository extends GenericRepository<User, Long> {

    User findByUsername(String userName);

    List<User> findByActiveFlag(boolean activeFlag);

}
