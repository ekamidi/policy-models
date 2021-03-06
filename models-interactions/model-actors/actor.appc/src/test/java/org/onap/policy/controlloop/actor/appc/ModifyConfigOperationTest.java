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

package org.onap.policy.controlloop.actor.appc;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.aai.domain.yang.GenericVnf;
import org.onap.policy.aai.AaiCqResponse;
import org.onap.policy.appc.Request;
import org.onap.policy.appc.Response;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.controlloop.actorserviceprovider.OperationOutcome;
import org.onap.policy.controlloop.actorserviceprovider.controlloop.ControlLoopEventContext;
import org.onap.policy.controlloop.actorserviceprovider.parameters.BidirectionalTopicConfig;
import org.onap.policy.controlloop.actorserviceprovider.parameters.BidirectionalTopicParams;
import org.onap.policy.controlloop.policy.PolicyResult;

public class ModifyConfigOperationTest extends BasicAppcOperation {

    private ModifyConfigOperation oper;

    public ModifyConfigOperationTest() {
        super(DEFAULT_ACTOR, ModifyConfigOperation.NAME);
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // use same topic name for both sides
        initBeforeClass(MY_SINK, MY_SINK);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        destroyAfterClass();
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        oper = new ModifyConfigOperation(params, config);
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    /**
     * Tests "success" case with simulator.
     */
    @Test
    public void testSuccess() throws Exception {
        BidirectionalTopicParams opParams =
                        BidirectionalTopicParams.builder().sinkTopic(MY_SINK).sourceTopic(MY_SINK).build();
        config = new BidirectionalTopicConfig(blockingExecutor, opParams, topicMgr, AppcOperation.SELECTOR_KEYS);

        AaiCqResponse cq = mock(AaiCqResponse.class);
        GenericVnf genvnf = mock(GenericVnf.class);
        when(genvnf.getVnfId()).thenReturn(MY_VNF);
        when(cq.getGenericVnfByModelInvariantId(any())).thenReturn(genvnf);

        params.getContext().setProperty(AaiCqResponse.CONTEXT_KEY, cq);

        params = params.toBuilder().retry(0).timeoutSec(5).executor(blockingExecutor).build();

        oper = new ModifyConfigOperation(params, config) {
            @Override
            protected CompletableFuture<OperationOutcome> startGuardAsync() {
                return null;
            }
        };

        outcome = oper.start().get();
        assertEquals(PolicyResult.SUCCESS, outcome.getResult());
        assertTrue(outcome.getResponse() instanceof Response);
    }

    @Test
    public void testConstructor() {
        assertEquals(DEFAULT_ACTOR, oper.getActorName());
        assertEquals(ModifyConfigOperation.NAME, oper.getName());
    }

    @Test
    public void testStartPreprocessorAsync() throws Exception {
        CompletableFuture<OperationOutcome> future2 = new CompletableFuture<>();
        context = mock(ControlLoopEventContext.class);
        when(context.obtain(eq(AaiCqResponse.CONTEXT_KEY), any())).thenReturn(future2);
        when(context.getEvent()).thenReturn(event);
        params = params.toBuilder().context(context).build();

        AtomicBoolean guardStarted = new AtomicBoolean();

        oper = new ModifyConfigOperation(params, config) {
            @Override
            protected CompletableFuture<OperationOutcome> startGuardAsync() {
                guardStarted.set(true);
                return super.startGuardAsync();
            }
        };

        CompletableFuture<OperationOutcome> future3 = oper.startPreprocessorAsync();
        assertNotNull(future3);
        assertFalse(future.isDone());
        assertTrue(guardStarted.get());
        verify(context).obtain(eq(AaiCqResponse.CONTEXT_KEY), any());

        future2.complete(params.makeOutcome());
        assertTrue(executor.runAll(100));
        assertTrue(future3.isDone());
        assertEquals(PolicyResult.SUCCESS, future3.get().getResult());
    }

    @Test
    public void testMakeRequest() throws CoderException {
        AaiCqResponse cq = new AaiCqResponse("{}");

        // missing vnf-id
        params.getContext().setProperty(AaiCqResponse.CONTEXT_KEY, cq);
        assertThatIllegalArgumentException().isThrownBy(() -> oper.makeRequest(1));

        // populate the CQ data with a vnf-id
        GenericVnf genvnf = new GenericVnf();
        genvnf.setVnfId(MY_VNF);
        genvnf.setModelInvariantId(RESOURCE_ID);
        cq.setInventoryResponseItems(Arrays.asList(genvnf));

        oper.generateSubRequestId(2);
        Request request = oper.makeRequest(2);
        assertNotNull(request);
        assertEquals(MY_VNF, request.getPayload().get(ModifyConfigOperation.VNF_ID_KEY));

        verifyRequest("modifyConfig.json", request, IGNORE_FIELDS);
    }
}
