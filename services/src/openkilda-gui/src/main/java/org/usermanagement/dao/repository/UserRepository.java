package org.usermanagement.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.UserEntity;

/**
 * The Interface UserRepository.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Find by username.
     *
     * @param userName the user name
     * @return the user entity
     */
    UserEntity findByUsername(String userName);

    /**
     * Find by active flag.
     *
     * @param activeFlag the active flag
     * @return the list
     */
    List<UserEntity> findByActiveFlag(boolean activeFlag);

    /**
     * Find byroles.
     *
     * @param roleEntity the role entity
     * @return the list
     */
    List<UserEntity> findByroles(RoleEntity roleEntity);

    /**
     * @param userId
     * @return
     */
    UserEntity findByUserId(long userId);

    List<UserEntity> findByRoles_roleId(Long roleId);
}
