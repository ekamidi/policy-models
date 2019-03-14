/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019 Nordix Foundation.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.models.tosca.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;

import org.junit.Test;
import org.onap.policy.models.base.PfConceptKey;
import org.onap.policy.models.base.PfReferenceKey;
import org.onap.policy.models.base.PfValidationResult;

/**
 * DAO test for ToscaTimeInterval.
 *
 * @author Liam Fallon (liam.fallon@est.tech)
 */
public class ToscaTimeIntervalTest {

    @Test
    public void testTimeIntervalPojo() {
        assertNotNull(new ToscaTimeInterval());
        assertNotNull(new ToscaTimeInterval(new PfReferenceKey()));
        assertNotNull(new ToscaTimeInterval(new PfReferenceKey(), new Date(), new Date()));
        assertNotNull(new ToscaTimeInterval(new ToscaTimeInterval()));

        try {
            new ToscaTimeInterval((PfReferenceKey) null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("key is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(null, null, null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("key is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(null, null, new Date());
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("key is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(null, new Date(), null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("key is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(null, new Date(), new Date());
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("key is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(new PfReferenceKey(), null, null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("startTime is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(new PfReferenceKey(), null, new Date());
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("startTime is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval(new PfReferenceKey(), new Date(), null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("endTime is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new ToscaTimeInterval((ToscaTimeInterval) null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("copyConcept is marked @NonNull but is null", exc.getMessage());
        }

        PfConceptKey ttiParentKey = new PfConceptKey("tParentKey", "0.0.1");
        PfReferenceKey ttiKey = new PfReferenceKey(ttiParentKey, "trigger0");
        Date startTime = new Date(1000);
        Date endTime = new Date(2000);
        ToscaTimeInterval tti = new ToscaTimeInterval(ttiKey, startTime, endTime);

        ToscaTimeInterval tdtClone0 = new ToscaTimeInterval(tti);
        assertEquals(tti, tdtClone0);
        assertEquals(0, tti.compareTo(tdtClone0));

        ToscaTimeInterval tdtClone1 = new ToscaTimeInterval();
        tti.copyTo(tdtClone1);
        assertEquals(tti, tdtClone1);
        assertEquals(0, tti.compareTo(tdtClone1));

        assertEquals(-1, tti.compareTo(null));
        assertEquals(0, tti.compareTo(tti));
        assertFalse(tti.compareTo(tti.getKey()) == 0);

        PfReferenceKey otherDtKey = new PfReferenceKey("otherDt", "0.0.1", "OtherTimeInterval");
        ToscaTimeInterval otherDt = new ToscaTimeInterval(otherDtKey);

        assertFalse(tti.compareTo(otherDt) == 0);
        otherDt.setKey(ttiKey);
        assertFalse(tti.compareTo(otherDt) == 0);
        otherDt.setStartTime(startTime);
        assertFalse(tti.compareTo(otherDt) == 0);
        otherDt.setEndTime(endTime);
        assertEquals(0, tti.compareTo(otherDt));

        try {
            tti.copyTo(null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("target is marked @NonNull but is null", exc.getMessage());
        }

        assertEquals(1, tti.getKeys().size());
        assertEquals(1, new ToscaTimeInterval().getKeys().size());

        new ToscaTimeInterval().clean();
        tti.clean();
        assertEquals(tdtClone0, tti);

        assertFalse(new ToscaTimeInterval().validate(new PfValidationResult()).isValid());
        assertTrue(tti.validate(new PfValidationResult()).isValid());

        tti.setStartTime(null);
        assertFalse(tti.validate(new PfValidationResult()).isValid());
        tti.setStartTime(new Date(endTime.getTime() + 1));
        assertFalse(tti.validate(new PfValidationResult()).isValid());
        tti.setStartTime(startTime);
        assertTrue(tti.validate(new PfValidationResult()).isValid());

        tti.setEndTime(null);
        assertFalse(tti.validate(new PfValidationResult()).isValid());
        tti.setEndTime(new Date(startTime.getTime() - 1));
        assertFalse(tti.validate(new PfValidationResult()).isValid());
        tti.setEndTime(endTime);
        assertTrue(tti.validate(new PfValidationResult()).isValid());

        try {
            tti.validate(null);
            fail("test should throw an exception");
        } catch (Exception exc) {
            assertEquals("resultIn is marked @NonNull but is null", exc.getMessage());
        }
    }
}
