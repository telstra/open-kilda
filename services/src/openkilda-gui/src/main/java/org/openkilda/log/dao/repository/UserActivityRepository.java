package org.openkilda.log.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import org.openkilda.log.dao.entity.UserActivityEntity;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface UserActivityRepository extends JpaRepository<UserActivityEntity, Long> {

	public List<UserActivityEntity> findByActivityTimeGreaterThanEqualAndActivityTimeLessThanEqual(Date startTime,
			Date endTime);

	public List<UserActivityEntity> findByUserIdInAndActivityTimeGreaterThanEqualAndActivityTimeLessThanEqual(
			List<Long> userIds, Date startTime, Date endTime);

	public List<UserActivityEntity> findByActivityTimeGreaterThanEqualAndActivityTimeLessThanEqualAndActivity_IdIn(
			Date startTime, Date endTime, List<Long> id);

	public List<UserActivityEntity> findByUserIdInAndActivityTimeGreaterThanEqualAndActivityTimeLessThanEqualAndActivity_IdIn(
			List<Long> userIds, Date startTime, Date endTime, List<Long> ids);
	
}
