package org.openkilda.auth.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define permissions at method level.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Permissions {

	/**
	 * Returns permission values.
	 *
	 * @return permission values.
	 */
	String[] values();

	/**
	 * Check object access permissions.
	 *
	 * @return true, if successful
	 */
	boolean checkObjectAccessPermissions() default false;
}
