package org.openkilda.dao;

import org.openkilda.entity.Role;
import org.springframework.stereotype.Repository;

/**
 * 
 * @author
 *
 */
@Repository
public interface RoleRepository extends GenericRepository<Role, Long> {

    Role findByUserRoleId(Long userRoleId);

}
