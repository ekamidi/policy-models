/*-
 * ============LICENSE_START=======================================================
 * sdc
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019-2020 Nordix Foundation.
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

package org.onap.policy.sdc;

import java.io.Serializable;
import java.util.UUID;
import lombok.Data;

@Data
public class ServiceInstance implements Serializable {

    private static final long serialVersionUID = 6285260780966679625L;

    /*
     * Note the field names ending in "UUID" may not be changed without breaking the
     * interface, due to limitations in the YAML encoder/decoder.
     */
    private UUID personaModelUUID;
    private UUID serviceUUID;
    private UUID serviceInstanceUUID;
    private UUID widgetModelUUID;

    private String widgetModelVersion;
    private String serviceName;
    private String serviceInstanceName;

    public ServiceInstance() {
        // Empty Constructor
    }

    /**
     * Constructor.
     *
     * @param instance copy object
     */
    public ServiceInstance(ServiceInstance instance) {
        if (instance == null) {
            return;
        }
        this.personaModelUUID = instance.personaModelUUID;
        this.serviceUUID = instance.serviceUUID;
        this.serviceInstanceUUID = instance.serviceInstanceUUID;
        this.widgetModelUUID = instance.widgetModelUUID;
        this.widgetModelVersion = instance.widgetModelVersion;
        this.serviceName = instance.serviceName;
        this.serviceInstanceName = instance.serviceInstanceName;
    }
}
