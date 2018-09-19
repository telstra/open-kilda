/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.usermanagement.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:mail.properties")
public class MailUtils {

    @Value("${subject.account.username}")
    private String subjectAccountUsername;


    @Value("${subject.account.password}")
    private String subjectAccountPassword;

    @Value("${subject.reset.twofa}")
    private String subjectReset2fa;

    @Value("${subject.reset.password}")
    private String subjectResetPassword;

    @Value("${subject.change.password}")
    private String subjectChangePassword;


    public String getSubjectAccountUsername() {
        return subjectAccountUsername;
    }



    public void setSubjectAccountUsername(String subjectAccountUsername) {
        this.subjectAccountUsername = subjectAccountUsername;
    }



    public String getSubjectAccountPassword() {
        return subjectAccountPassword;
    }



    public void setSubjectAccountPassword(String subjectAccountPassword) {
        this.subjectAccountPassword = subjectAccountPassword;
    }



    public String getSubjectReset2fa() {
        return subjectReset2fa;
    }



    public void setSubjectReset2fa(String subjectReset2fa) {
        this.subjectReset2fa = subjectReset2fa;
    }



    public String getSubjectResetPassword() {
        return subjectResetPassword;
    }



    public void setSubjectResetPassword(String subjectResetPassword) {
        this.subjectResetPassword = subjectResetPassword;
    }



    public String getSubjectChangePassword() {
        return subjectChangePassword;
    }



    public void setSubjectChangePassword(String subjectChangePassword) {
        this.subjectChangePassword = subjectChangePassword;
    }



}
