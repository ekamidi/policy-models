/*-
 * ============LICENSE_START=======================================================
 * sdnr
 * ================================================================================
 * Copyright (C) 2018 Wipro Limited Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019 Nordix Foundation.
 * Modifications Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.sdnr;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PciResponseTest {

    Status status = new Status(0, "");

    String responsePayload = "";
    String requestPayload = "";


    @Test
    public void testHashCode() {
        PciResponse response = new PciResponse();
        assertTrue(response.hashCode() != 0);
        response.setCommonHeader(new PciCommonHeader());
        assertTrue(response.hashCode() != 0);
        response.setPayload(responsePayload);
        assertTrue(response.hashCode() != 0);
        response.setStatus(null);
        assertTrue(response.hashCode() != 0);
    }

    @Test
    public void testPciResponse() {
        PciResponse response = new PciResponse();
        assertNull(response.getCommonHeader());
        assertNull(response.getPayload());
        assertNotNull(response.getStatus());
    }

    @Test
    public void testToString() {
        PciResponse response = new PciResponse();
        assertFalse(response.toString().isEmpty());
    }

    @Test
    public void testEqualsObject() {
        PciResponse response = new PciResponse();
        assertTrue(response.equals(response));
        assertFalse(response.equals(null));
        assertFalse(response.equals(new Object()));

        PciResponse response2 = new PciResponse();
        assertTrue(response.equals(response2));

        response.setCommonHeader(new PciCommonHeader());
        assertFalse(response.equals(response2));
        response2.setCommonHeader(response.getCommonHeader());
        assertTrue(response.equals(response2));

        response.setPayload(responsePayload);
        assertFalse(response.equals(response2));
        response2.setPayload(response.getPayload());
        assertTrue(response.equals(response2));

        response.setCommonHeader(null);
        assertFalse(response.equals(response2));
        response2.setCommonHeader(null);
        assertTrue(response.equals(response2));

        response.setPayload(null);
        assertFalse(response.equals(response2));
        response2.setPayload(response.getPayload());
        assertTrue(response.equals(response2));

        response.setStatus(null);
        assertFalse(response.equals(response2));
        response2.setStatus(response.getStatus());
        assertTrue(response.equals(response2));

        Status stat = new Status();
        stat.setCode(5);
        response.setStatus(stat);
        response2.setStatus(new Status());
        assertFalse(response.equals(response2));
    }

    @Test
    public void testResponseRequest() {
        PciRequest request = new PciRequest();
        request.setCommonHeader(new PciCommonHeader());
        request.setPayload(requestPayload);

        PciResponse response = new PciResponse(request);

        assertTrue(response.getCommonHeader().equals(request.getCommonHeader()));
    }

}
