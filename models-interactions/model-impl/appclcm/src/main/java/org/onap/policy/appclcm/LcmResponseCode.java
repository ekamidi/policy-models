/*-
 * ============LICENSE_START=======================================================
 * appclcm
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019 Nordix Foundation.
 * Modifications Copyright (C) 2018 Samsung Electronics Co., Ltd.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.appclcm;

import java.io.Serializable;
import org.onap.policy.appclcm.util.StatusCodeEnum;

public class LcmResponseCode implements Serializable {

    /* These fields define the key to the response code value. */
    public static final String ACCEPTED = "ACCEPTED";
    public static final String ERROR = "ERROR";
    public static final String REJECT = "REJECT";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    public static final String PARTIAL_SUCCESS = "PARTIAL SUCCESS";
    public static final String PARTIAL_FAILURE = "PARTIAL FAILURE";
    private static final long serialVersionUID = 8189456447227022582L;

    private final Integer code;

    protected LcmResponseCode(final int code) {
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

    @Override
    public String toString() {
        return Integer.toString(this.code);
    }

    /**
     * Translates the code to a string value that represents the meaning of the code.
     *
     * @param code the numeric value that is returned by APPC based on success, failure, etc. of the action requested
     * @return the string value equivalent of the APPC response code
     */
    public static String toResponseValue(int code) {
        StatusCodeEnum statusCodeEnum = StatusCodeEnum.fromStatusCode(code);
        return (statusCodeEnum != null) ? statusCodeEnum.toString() : null;
    }
}
