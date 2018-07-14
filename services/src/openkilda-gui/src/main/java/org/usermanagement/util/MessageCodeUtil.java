package org.usermanagement.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:message.properties")
public class MessageCodeUtil {
	
    @Value("${error.code.prefix}")
    private String errorCodePrefix;
    
    @Value("${attribute.not.found.code}")
    private String attributeNotFoundCode;

	public int getAttributeNotFoundCode() {
		return Integer.parseInt(errorCodePrefix + attributeNotFoundCode);
	}
}
