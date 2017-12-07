package org.openkilda.handler.error;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * The Class MyAccessDeniedHandler.
 */
// handle 403 page
@Component
public class MyAccessDeniedHandler implements AccessDeniedHandler {

	/** The logger. */
	private static Logger logger = LoggerFactory
			.getLogger(MyAccessDeniedHandler.class);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.security.web.access.AccessDeniedHandler#handle(javax
	 * .servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse,
	 * org.springframework.security.access.AccessDeniedException)
	 */
	@Override
	public void handle(HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, AccessDeniedException e)
			throws IOException, ServletException {

		Authentication auth = SecurityContextHolder.getContext()
				.getAuthentication();

		if (auth != null) {
			logger.info("User '" + auth.getName()
					+ "' attempted to access the protected URL: "
					+ httpServletRequest.getRequestURI());
		}

		httpServletResponse.sendRedirect(httpServletRequest.getContextPath()
				+ "/403");

	}
}
