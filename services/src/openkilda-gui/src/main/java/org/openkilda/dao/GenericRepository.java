package org.openkilda.dao;

import java.io.Serializable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * The Interface GenericRepository.
 *
 * @author Gaurav Chugh
 * @param <T>
 *            the generic type
 * @param <ID>
 *            the generic type
 */
@NoRepositoryBean
public interface GenericRepository<T, ID extends Serializable> extends
		JpaRepository<T, ID> {

}
