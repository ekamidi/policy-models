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

package org.onap.policy.controlloop.actor.so;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import lombok.Getter;
import org.onap.aai.domain.yang.CloudRegion;
import org.onap.aai.domain.yang.GenericVnf;
import org.onap.aai.domain.yang.ServiceInstance;
import org.onap.aai.domain.yang.Tenant;
import org.onap.policy.aai.AaiConstants;
import org.onap.policy.aai.AaiCqResponse;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.utils.NetLoggerUtil.EventType;
import org.onap.policy.common.utils.coder.Coder;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.controlloop.actorserviceprovider.OperationOutcome;
import org.onap.policy.controlloop.actorserviceprovider.impl.HttpOperation;
import org.onap.policy.controlloop.actorserviceprovider.parameters.ControlLoopOperationParams;
import org.onap.policy.controlloop.actorserviceprovider.parameters.HttpConfig;
import org.onap.policy.controlloop.policy.PolicyResult;
import org.onap.policy.controlloop.policy.Target;
import org.onap.policy.so.SoModelInfo;
import org.onap.policy.so.SoRequest;
import org.onap.policy.so.SoRequestInfo;
import org.onap.policy.so.SoRequestParameters;
import org.onap.policy.so.SoRequestStatus;
import org.onap.policy.so.SoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superclass for SDNC Operators. Note: subclasses should invoke {@link #resetGetCount()}
 * each time they issue an HTTP request.
 */
public abstract class SoOperation extends HttpOperation<SoResponse> {
    private static final Logger logger = LoggerFactory.getLogger(SoOperation.class);
    private static final Coder coder = new StandardCoder();

    public static final String FAILED = "FAILED";
    public static final String COMPLETE = "COMPLETE";
    public static final int SO_RESPONSE_CODE = 999;

    // fields within the policy payload
    public static final String REQ_PARAM_NM = "requestParameters";
    public static final String CONFIG_PARAM_NM = "configurationParameters";

    private final SoConfig config;

    // values extracted from the parameter Target
    private final String modelCustomizationId;
    private final String modelInvariantId;
    private final String modelVersionId;

    private final String vfCountKey;

    /**
     * Number of "get" requests issued so far, on the current operation attempt.
     */
    @Getter
    private int getCount;


    /**
     * Constructs the object.
     *
     * @param params operation parameters
     * @param config configuration for this operation
     */
    public SoOperation(ControlLoopOperationParams params, HttpConfig config) {
        super(params, config, SoResponse.class);
        this.config = (SoConfig) config;

        verifyNotNull("Target information", params.getTarget());

        this.modelCustomizationId = params.getTarget().getModelCustomizationId();
        this.modelInvariantId = params.getTarget().getModelInvariantId();
        this.modelVersionId = params.getTarget().getModelVersionId();

        vfCountKey = SoConstants.VF_COUNT_PREFIX + "[" + modelCustomizationId + "][" + modelInvariantId + "]["
                        + modelVersionId + "]";
    }

    /**
     * Subclasses should invoke this before issuing their first HTTP request.
     */
    protected void resetGetCount() {
        getCount = 0;
    }

    /**
     * Validates that the parameters contain the required target information to extract
     * the VF count from the custom query.
     */
    protected void validateTarget() {
        verifyNotNull("model-customization-id", modelCustomizationId);
        verifyNotNull("model-invariant-id", modelInvariantId);
        verifyNotNull("model-version-id", modelVersionId);
    }

    private void verifyNotNull(String type, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("missing " + type + " for guard payload");
        }
    }

    /**
     * Starts the GUARD.
     */
    @Override
    protected CompletableFuture<OperationOutcome> startPreprocessorAsync() {
        return startGuardAsync();
    }

    /**
     * Gets the VF Count.
     *
     * @return a future to cancel or await the VF Count
     */
    @SuppressWarnings("unchecked")
    protected CompletableFuture<OperationOutcome> obtainVfCount() {
        if (params.getContext().contains(vfCountKey)) {
            // already have the VF count
            return null;
        }

        // need custom query from which to extract the VF count
        ControlLoopOperationParams cqParams = params.toBuilder().actor(AaiConstants.ACTOR_NAME)
                        .operation(AaiCqResponse.OPERATION).payload(null).retry(null).timeoutSec(null).build();

        // run Custom Query and then extract the VF count
        return sequence(() -> params.getContext().obtain(AaiCqResponse.CONTEXT_KEY, cqParams), this::storeVfCount);
    }

    /**
     * Stores the VF count.
     *
     * @return {@code null}
     */
    private CompletableFuture<OperationOutcome> storeVfCount() {
        if (!params.getContext().contains(vfCountKey)) {
            AaiCqResponse cq = params.getContext().getProperty(AaiCqResponse.CONTEXT_KEY);
            int vfcount = cq.getVfModuleCount(modelCustomizationId, modelInvariantId, modelVersionId);

            params.getContext().setProperty(vfCountKey, vfcount);
        }

        return null;
    }

    protected int getVfCount() {
        return params.getContext().getProperty(vfCountKey);
    }

    protected void setVfCount(int vfCount) {
        params.getContext().setProperty(vfCountKey, vfCount);
    }

    /**
     * If the response does not indicate that the request has been completed, then sleep a
     * bit and issue a "get".
     */
    @Override
    protected CompletableFuture<OperationOutcome> postProcessResponse(OperationOutcome outcome, String url,
                    Response rawResponse, SoResponse response) {

        // see if the request has "completed", whether or not it was successful
        if (rawResponse.getStatus() == 200) {
            String requestState = getRequestState(response);
            if (COMPLETE.equalsIgnoreCase(requestState)) {
                successfulCompletion();
                return CompletableFuture
                                .completedFuture(setOutcome(outcome, PolicyResult.SUCCESS, rawResponse, response));
            }

            if (FAILED.equalsIgnoreCase(requestState)) {
                return CompletableFuture
                                .completedFuture(setOutcome(outcome, PolicyResult.FAILURE, rawResponse, response));
            }
        }

        // still incomplete

        // need a request ID with which to query
        if (response.getRequestReferences() == null || response.getRequestReferences().getRequestId() == null) {
            throw new IllegalArgumentException("missing request ID in response");
        }

        // see if the limit for the number of "gets" has been reached
        if (getCount++ >= getMaxGets()) {
            logger.warn("{}: execeeded 'get' limit {} for {}", getFullName(), getMaxGets(), params.getRequestId());
            setOutcome(outcome, PolicyResult.FAILURE_TIMEOUT);
            outcome.setMessage(SO_RESPONSE_CODE + " " + outcome.getMessage());
            return CompletableFuture.completedFuture(outcome);
        }

        // sleep and then perform a "get" operation
        Function<Void, CompletableFuture<OperationOutcome>> doGet = unused -> issueGet(outcome, response);
        return sleep(getWaitMsGet(), TimeUnit.MILLISECONDS).thenComposeAsync(doGet);
    }

    /**
     * Invoked when a request completes successfully.
     */
    protected void successfulCompletion() {
        // do nothing
    }

    /**
     * Issues a "get" request to see if the original request is complete yet.
     *
     * @param outcome outcome to be populated with the response
     * @param response previous response
     * @return a future that can be used to cancel the "get" request or await its response
     */
    private CompletableFuture<OperationOutcome> issueGet(OperationOutcome outcome, SoResponse response) {
        String path = getPathGet() + response.getRequestReferences().getRequestId();
        String url = getClient().getBaseUrl() + path;

        logger.debug("{}: 'get' count {} for {}", getFullName(), getCount, params.getRequestId());

        logMessage(EventType.OUT, CommInfrastructure.REST, url, null);

        // TODO should this use "path" or the full "url"?
        return handleResponse(outcome, url, callback -> getClient().get(callback, path, null));
    }

    /**
     * Gets the request state of a response.
     *
     * @param response response from which to get the state
     * @return the request state of the response, or {@code null} if it does not exist
     */
    protected String getRequestState(SoResponse response) {
        SoRequest request = response.getRequest();
        if (request == null) {
            return null;
        }

        SoRequestStatus status = request.getRequestStatus();
        if (status == null) {
            return null;
        }

        return status.getRequestState();
    }

    /**
     * Treats everything as a success, so we always go into
     * {@link #postProcessResponse(OperationOutcome, String, Response, SoResponse)}.
     */
    @Override
    protected boolean isSuccess(Response rawResponse, SoResponse response) {
        return true;
    }

    /**
     * Prepends the message with the http status code.
     */
    @Override
    public OperationOutcome setOutcome(OperationOutcome outcome, PolicyResult result, Response rawResponse,
                    SoResponse response) {

        // set default result and message
        setOutcome(outcome, result);

        outcome.setMessage(rawResponse.getStatus() + " " + outcome.getMessage());
        return outcome;
    }

    protected SoModelInfo prepareSoModelInfo() {
        Target target = params.getTarget();
        if (target == null) {
            throw new IllegalArgumentException("missing Target");
        }

        if (target.getModelCustomizationId() == null || target.getModelInvariantId() == null
                        || target.getModelName() == null || target.getModelVersion() == null
                        || target.getModelVersionId() == null) {
            throw new IllegalArgumentException("missing VF Module model");
        }

        SoModelInfo soModelInfo = new SoModelInfo();
        soModelInfo.setModelCustomizationId(target.getModelCustomizationId());
        soModelInfo.setModelInvariantId(target.getModelInvariantId());
        soModelInfo.setModelName(target.getModelName());
        soModelInfo.setModelVersion(target.getModelVersion());
        soModelInfo.setModelVersionId(target.getModelVersionId());
        soModelInfo.setModelType("vfModule");
        return soModelInfo;
    }

    /**
     * Construct requestInfo for the SO requestDetails.
     *
     * @return SO request information
     */
    protected SoRequestInfo constructRequestInfo() {
        SoRequestInfo soRequestInfo = new SoRequestInfo();
        soRequestInfo.setSource("POLICY");
        soRequestInfo.setSuppressRollback(false);
        soRequestInfo.setRequestorId("policy");
        return soRequestInfo;
    }

    /**
     * Builds the request parameters from the policy payload.
     */
    protected Optional<SoRequestParameters> buildRequestParameters() {
        if (params.getPayload() == null) {
            return Optional.empty();
        }

        Object data = params.getPayload().get(REQ_PARAM_NM);
        if (data == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(coder.decode(data.toString(), SoRequestParameters.class));
        } catch (CoderException e) {
            throw new IllegalArgumentException("invalid payload value: " + REQ_PARAM_NM);
        }
    }

    /**
     * Builds the configuration parameters from the policy payload.
     */
    protected Optional<List<Map<String, String>>> buildConfigurationParameters() {
        if (params.getPayload() == null) {
            return Optional.empty();
        }

        Object data = params.getPayload().get(CONFIG_PARAM_NM);
        if (data == null) {
            return Optional.empty();
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> result = coder.decode(data.toString(), ArrayList.class);
            return Optional.of(result);
        } catch (CoderException | RuntimeException e) {
            throw new IllegalArgumentException("invalid payload value: " + CONFIG_PARAM_NM);
        }
    }

    /*
     * These methods extract data from the Custom Query and throw an
     * IllegalArgumentException if the desired data item is not found.
     */

    protected GenericVnf getVnfItem(AaiCqResponse aaiCqResponse, SoModelInfo soModelInfo) {
        GenericVnf vnf = aaiCqResponse.getGenericVnfByVfModuleModelInvariantId(soModelInfo.getModelInvariantId());
        if (vnf == null) {
            throw new IllegalArgumentException("missing generic VNF");
        }

        return vnf;
    }

    protected ServiceInstance getServiceInstance(AaiCqResponse aaiCqResponse) {
        ServiceInstance vnfService = aaiCqResponse.getServiceInstance();
        if (vnfService == null) {
            throw new IllegalArgumentException("missing VNF Service Item");
        }

        return vnfService;
    }

    protected Tenant getDefaultTenant(AaiCqResponse aaiCqResponse) {
        Tenant tenant = aaiCqResponse.getDefaultTenant();
        if (tenant == null) {
            throw new IllegalArgumentException("missing Tenant Item");
        }

        return tenant;
    }

    protected CloudRegion getDefaultCloudRegion(AaiCqResponse aaiCqResponse) {
        CloudRegion cloudRegion = aaiCqResponse.getDefaultCloudRegion();
        if (cloudRegion == null) {
            throw new IllegalArgumentException("missing Cloud Region");
        }

        return cloudRegion;
    }

    // these may be overridden by junit tests

    /**
     * Gets the wait time, in milliseconds, between "get" requests.
     *
     * @return the wait time, in milliseconds, between "get" requests
     */
    public long getWaitMsGet() {
        return TimeUnit.MILLISECONDS.convert(getWaitSecGet(), TimeUnit.SECONDS);
    }

    public int getMaxGets() {
        return config.getMaxGets();
    }

    public String getPathGet() {
        return config.getPathGet();
    }

    public int getWaitSecGet() {
        return config.getWaitSecGet();
    }
}