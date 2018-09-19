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

package org.usermanagement.exception;

public abstract class CustomException extends RuntimeException {
    private static final long serialVersionUID = 6970024132461386518L;
    private final Integer code;

    public CustomException() {
        this.code = null;
    }
    
    public CustomException(String message) {
        super(message);
        this.code = null;
    }

    public CustomException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public CustomException(int code) {
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, String message) {
        super(message);
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, Throwable cause) {
        super(cause);
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = Integer.valueOf(code);
    }

    public CustomException(int code, String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = Integer.valueOf(code);
    }

    public Integer getCode() {
        return this.code;
    }
}
