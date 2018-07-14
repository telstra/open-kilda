package org.usermanagement.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.usermanagement.dao.entity.StatusEntity;



/**
 * The Interface StatusRepository.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface StatusRepository extends JpaRepository<StatusEntity, Integer> {

    
}
