package org.openkilda.dao;

import org.openkilda.entity.Role;
import org.springframework.stereotype.Repository;

/**
 * The Interface RoleRepository.
 */
@Repository
public interface RoleRepository extends GenericRepository<Role, Long> {

    /**
     * Find by user role id.
     *
     * @param userRoleId the user role id
     * @return the role
     */
    Role findByUserRoleId(Long userRoleId);

}
