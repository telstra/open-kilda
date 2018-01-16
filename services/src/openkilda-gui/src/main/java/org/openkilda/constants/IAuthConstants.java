package org.openkilda.constants;

/**
 * The Class IAuthConstants.
 */
public abstract class IAuthConstants {

    private IAuthConstants() {}

    public interface Header {
        String AUTHORIZATION = "Authorization";
        String CORRELATION_ID = "correlationid";
        String BASIC_AUTH = "BasicAuth";
    }

    public interface ContentType {
        String JSON = "application/json";
    }

    public interface Code {
        int SUCCESS_CODE = 20000;
        String SUCCESS_AUXILIARY = "Success";
        String ERROR_AUXILIARY = "Error";
        
        String RESPONSE_PARSING_FAIL_ERROR = "response.parsing.fail.error";
    }
}
