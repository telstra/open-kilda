package org.usermanagement.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.usermanagement.dao.entity.UserSettingEntity;

/**
 * The Interface UserSettingRepository.
 */
@Repository
public interface UserSettingRepository extends JpaRepository<UserSettingEntity, Long> {

	public UserSettingEntity findOneByUserId(final long userId);
}
