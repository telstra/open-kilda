package org.usermanagement.context;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import org.usermanagement.model.UserInfo;

@RequestScope
@Component
public class RequestContext {

    private UserInfo userInfo;

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(final UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    @Override
    public String toString() {
        return "RequestContext [userInfo=" + userInfo + "]";
    }


}
