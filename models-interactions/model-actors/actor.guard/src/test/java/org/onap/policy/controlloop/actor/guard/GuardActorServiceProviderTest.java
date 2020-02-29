/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.controlloop.actor.guard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Test;
import org.onap.policy.controlloop.actor.test.BasicActor;

public class GuardActorServiceProviderTest extends BasicActor {

    @Test
    public void test() {
        final GuardActorServiceProvider prov = new GuardActorServiceProvider();

        // verify that it has the operators we expect
        var expected = Arrays.asList(GuardOperation.NAME).stream().sorted().collect(Collectors.toList());
        var actual = prov.getOperationNames().stream().sorted().collect(Collectors.toList());

        assertEquals(expected.toString(), actual.toString());

        // verify that it all plugs into the ActorService
        verifyActorService(GuardActorServiceProvider.NAME, "service.yaml");

        assertTrue(prov.getOperator(GuardOperation.NAME) instanceof GuardOperator);
    }
}
