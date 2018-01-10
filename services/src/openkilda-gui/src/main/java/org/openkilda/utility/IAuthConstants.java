package org.openkilda.utility;

/**
 * The Class IAuthConstants.
 */
public abstract class IAuthConstants {

    /**
     * Instantiates a new i auth constants.
     */
    private IAuthConstants() {}

    /**
     * The Interface Header.
     */
    public interface Header {

        /** The authorization. */
        String AUTHORIZATION = "Authorization";

        /** The correlation id. */
        String CORRELATION_ID = "correlationid";

        /** The basic auth. */
        String BASIC_AUTH = "BasicAuth";
    }

    /**
     * The Interface ContentType.
     */
    public interface ContentType {

        /** The json. */
        String JSON = "application/json";
    }

    /**
     * The Interface ApiUrl.
     */
    public interface ApiUrl {

        /** The validate token url. */
        String VALIDATE_TOKEN_URL = "/validate/token";

        /** The get childs url. */
        String GET_CHILDS_URL = "/customers/<customeruuid>/childcustomers/info";

        /** The get customer details url. */
        String GET_CUSTOMER_DETAILS_URL = "/customers/<customeruuid>";

        /** The get customer user mapping url. */
        String GET_CUSTOMER_USER_MAPPING_URL = "/customer/<customeruuid>/user/<useruuid>";
    }

    /**
     * The Interface Code.
     */
    public interface Code {

        /** The success code. */
        int SUCCESS_CODE = 20000;

        /** The success auxiliary. */
        String SUCCESS_AUXILIARY = "Success";

        /** The error auxiliary. */
        String ERROR_AUXILIARY = "Error";

        /** The auth success message. */
        String AUTH_SUCCESS_MESSAGE = "auth.success.message";

        /** The request incorrect error. */
        String REQUEST_INCORRECT_ERROR = "request.incorrect.error";

        /** The auth fail error. */
        String AUTH_FAIL_ERROR = "auth.fail.error";

        /** The invalid field error. */
        String INVALID_FIELD_ERROR = "invalid.field.error";

        /** The token validation fail error. */
        String TOKEN_VALIDATION_FAIL_ERROR = "token.validation.fail.error";

        /** The response parsing fail error. */
        String RESPONSE_PARSING_FAIL_ERROR = "response.parsing.fail.error";

        /** The get all child request fail error. */
        String GET_ALL_CHILD_REQUEST_FAIL_ERROR = "get.all.child.request.fail.error";

        /** The get customer detail request fail error. */
        String GET_CUSTOMER_DETAIL_REQUEST_FAIL_ERROR = "get.customer.detail.request.fail.error";

        /** The rest call failed error. */
        String REST_CALL_FAILED_ERROR = "rest.call.failed.error";
    }
}
