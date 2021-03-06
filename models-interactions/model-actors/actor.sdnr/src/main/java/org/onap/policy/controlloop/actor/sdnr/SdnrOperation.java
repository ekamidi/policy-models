/*-
 * ============LICENSE_START=======================================================
 * SdnrOperation
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

package org.onap.policy.controlloop.actor.sdnr;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.onap.policy.controlloop.VirtualControlLoopEvent;
import org.onap.policy.controlloop.actorserviceprovider.OperationOutcome;
import org.onap.policy.controlloop.actorserviceprovider.impl.BidirectionalTopicOperation;
import org.onap.policy.controlloop.actorserviceprovider.parameters.BidirectionalTopicConfig;
import org.onap.policy.controlloop.actorserviceprovider.parameters.ControlLoopOperationParams;
import org.onap.policy.controlloop.actorserviceprovider.topic.SelectorKey;
import org.onap.policy.controlloop.policy.PolicyResult;
import org.onap.policy.sdnr.PciBody;
import org.onap.policy.sdnr.PciCommonHeader;
import org.onap.policy.sdnr.PciMessage;
import org.onap.policy.sdnr.PciRequest;
import org.onap.policy.sdnr.PciResponse;
import org.onap.policy.sdnr.util.StatusCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SdnrOperation extends BidirectionalTopicOperation<PciMessage, PciMessage> {
    private static final Logger logger = LoggerFactory.getLogger(SdnrOperation.class);

    /**
     * Operation name as it should appear within config files.
     */
    public static final String NAME = "any";

    /**
     * Keys used to match the response with the request listener. The sub request ID is a
     * UUID, so it can be used to uniquely identify the response.
     * <p/>
     * Note: if these change, then {@link #getExpectedKeyValues(int, PciMessage)} must be
     * updated accordingly.
     */
    public static final List<SelectorKey> SELECTOR_KEYS =
                    List.of(new SelectorKey("body", "output", "CommonHeader", "SubRequestID"));

    public SdnrOperation(ControlLoopOperationParams params, BidirectionalTopicConfig config) {
        super(params, config, PciMessage.class);
    }

    /**
     * Note: these values must be in correspondence with {@link #SELECTOR_KEYS}.
     */
    @Override
    protected List<String> getExpectedKeyValues(int attempt, PciMessage request) {
        return List.of(getSubRequestId());
    }

    @Override
    protected CompletableFuture<OperationOutcome> startPreprocessorAsync() {
        return startGuardAsync();
    }

    /*
     * NOTE: This should avoid throwing exceptions, so that a ControlLoopResponse can be
     * added to the outcome. Consequently, it returns FAILURE if a required field is
     * missing from the response.
     */
    @Override
    protected Status detmStatus(String rawResponse, PciMessage responseWrapper) {
        PciResponse response = responseWrapper.getBody().getOutput();

        if (response.getStatus() == null) {
            logger.warn("SDNR response is missing the response status");
            return Status.FAILURE;
        }

        StatusCodeEnum code = StatusCodeEnum.fromStatusCode(response.getStatus().getCode());

        if (code == null) {
            logger.warn("unknown SDNR response status code: {}", response.getStatus().getCode());
            return Status.FAILURE;
        }

        switch (code) {
            case SUCCESS:
            case PARTIAL_SUCCESS:
                return Status.SUCCESS;
            case FAILURE:
            case PARTIAL_FAILURE:
                return Status.FAILURE;
            case ERROR:
            case REJECT:
                logger.warn("SDNR request was not accepted, code={}", code);
                return Status.FAILURE;
            case ACCEPTED:
            default:
                // awaiting a "final" response
                return Status.STILL_WAITING;
        }
    }

    /**
     * Sets the message to the status description, if available.
     */
    @Override
    public OperationOutcome setOutcome(OperationOutcome outcome, PolicyResult result, PciMessage responseWrapper) {
        outcome.setResponse(responseWrapper);

        if (responseWrapper.getBody() == null || responseWrapper.getBody().getOutput() == null) {
            return setOutcome(outcome, result);
        }

        PciResponse pciResponse = responseWrapper.getBody().getOutput();
        if (pciResponse.getStatus() == null || pciResponse.getStatus().getValue() == null) {
            return setOutcome(outcome, result);
        }

        outcome.setResult(result);
        outcome.setMessage(pciResponse.getStatus().getValue());
        return outcome;
    }

    @Override
    protected PciMessage makeRequest(int attempt) {
        VirtualControlLoopEvent onset = params.getContext().getEvent();
        String subRequestId = getSubRequestId();

        /* Construct an SDNR request using pci Model */

        PciMessage dmaapRequest = new PciMessage();
        dmaapRequest.setVersion("1.0");
        dmaapRequest.setCorrelationId(onset.getRequestId() + "-" + subRequestId);
        dmaapRequest.setType("request");
        dmaapRequest.setRpcName(params.getOperation().toLowerCase());

        /* This is the actual request that is placed in the dmaap wrapper. */
        final PciRequest sdnrRequest = new PciRequest();

        /* The common header is a required field for all SDNR requests. */
        PciCommonHeader requestCommonHeader = new PciCommonHeader();
        requestCommonHeader.setRequestId(onset.getRequestId());
        requestCommonHeader.setSubRequestId(subRequestId);

        sdnrRequest.setCommonHeader(requestCommonHeader);
        sdnrRequest.setPayload(onset.getPayload());
        sdnrRequest.setAction(params.getOperation());

        /*
         * Once the pci request is constructed, add it into the body of the dmaap wrapper.
         */
        PciBody body = new PciBody();
        body.setInput(sdnrRequest);
        dmaapRequest.setBody(body);

        /* Return the request to be sent through dmaap. */
        return dmaapRequest;
    }
}
