/*
 * ============LICENSE_START=======================================================
 * SOActorServiceProvider
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019 Nordix Foundation.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.drools.core.WorkingMemory;
import org.onap.policy.aai.AaiNqExtraProperty;
import org.onap.policy.aai.AaiNqInventoryResponseItem;
import org.onap.policy.aai.AaiNqResponseWrapper;
import org.onap.policy.controlloop.ControlLoopOperation;
import org.onap.policy.controlloop.VirtualControlLoopEvent;
import org.onap.policy.controlloop.actorserviceprovider.spi.Actor;
import org.onap.policy.controlloop.policy.Policy;
import org.onap.policy.so.SoCloudConfiguration;
import org.onap.policy.so.SoManager;
import org.onap.policy.so.SoModelInfo;
import org.onap.policy.so.SoOperationType;
import org.onap.policy.so.SoRelatedInstance;
import org.onap.policy.so.SoRelatedInstanceListElement;
import org.onap.policy.so.SoRequest;
import org.onap.policy.so.SoRequestDetails;
import org.onap.policy.so.SoRequestInfo;
import org.onap.policy.so.SoRequestParameters;
import org.onap.policy.so.util.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoActorServiceProvider implements Actor {
    private static final Logger logger = LoggerFactory.getLogger(SoActorServiceProvider.class);

    // Strings for SO Actor
    private static final String SO_ACTOR = "SO";

    // Strings for targets
    private static final String TARGET_VFC = "VFC";

    // Strings for recipes
    private static final String RECIPE_VF_MODULE_CREATE = "VF Module Create";
    private static final String RECIPE_VF_MODULE_DELETE = "VF Module Delete";

    private static final ImmutableList<String> recipes = ImmutableList.of(RECIPE_VF_MODULE_CREATE,
            RECIPE_VF_MODULE_DELETE);
    private static final ImmutableMap<String, List<String>> targets = new ImmutableMap.Builder<String, List<String>>()
                    .put(RECIPE_VF_MODULE_CREATE, ImmutableList.of(TARGET_VFC))
                    .put(RECIPE_VF_MODULE_DELETE, ImmutableList.of(TARGET_VFC)).build();

    // name of request parameters within policy payload
    public static final String REQ_PARAM_NM = "requestParameters";

    // name of configuration parameters within policy payload
    public static final String CONFIG_PARAM_NM = "configurationParameters";

    private static final String MODEL_NAME_PROPERTY_KEY = "model-ver.model-name";
    private static final String MODEL_VERSION_PROPERTY_KEY = "model-ver.model-version";
    private static final String MODEL_VERSION_ID_PROPERTY_KEY = "model-ver.model-version-id";

    // used to decode configuration parameters via gson
    private static final Type CONFIG_TYPE = new TypeToken<List<Map<String, String>>>() {}.getType();

    // Static variables required to hold the IDs of the last service item, VNF item and VF Module.
    // Note that in
    // a multithreaded deployment this WILL break
    private static String lastVNFItemVnfId;
    private static String lastServiceItemServiceInstanceId;
    private static String lastVfModuleItemVfModuleInstanceId;

    @Override
    public String actor() {
        return SO_ACTOR;
    }

    @Override
    public List<String> recipes() {
        return ImmutableList.copyOf(recipes);
    }

    @Override
    public List<String> recipeTargets(String recipe) {
        return ImmutableList.copyOf(targets.getOrDefault(recipe, Collections.emptyList()));
    }

    @Override
    public List<String> recipePayloads(String recipe) {
        return Collections.emptyList();
    }

    /**
     * Constructs a SO request conforming to the lcm API. The actual request is
     * constructed and then placed in a wrapper object used to send through DMAAP.
     *
     * @param onset the event that is reporting the alert for policy to perform an action
     * @param operation the control loop operation specifying the actor, operation,
     *        target, etc.
     * @param policy the policy the was specified from the yaml generated by CLAMP or
     *        through the Policy GUI/API
     * @param aaiResponseWrapper wrapper for AAI vserver named-query response
     * @return a SO request conforming to the lcm API using the DMAAP wrapper
     */
    public SoRequest constructRequest(VirtualControlLoopEvent onset, ControlLoopOperation operation, Policy policy,
                    AaiNqResponseWrapper aaiResponseWrapper) {
        if (!SO_ACTOR.equals(policy.getActor()) || !recipes().contains(policy.getRecipe())) {
            return null;
        }

        // A&AI named query should have been performed by now. If not, return null
        if (aaiResponseWrapper == null) {
            return null;
        }

        AaiNqInventoryResponseItem vnfItem;
        AaiNqInventoryResponseItem vnfServiceItem;
        AaiNqInventoryResponseItem tenantItem;

        // Extract the items we're interested in from the response
        try {
            vnfItem = aaiResponseWrapper.getAaiNqResponse().getInventoryResponseItems().get(0).getItems()
                            .getInventoryResponseItems().get(0);
        } catch (Exception e) {
            logger.error("VNF Item not found in AAI response {}", Serialization.gsonPretty.toJson(aaiResponseWrapper),
                            e);
            return null;
        }

        try {
            vnfServiceItem = vnfItem.getItems().getInventoryResponseItems().get(0);
        } catch (Exception e) {
            logger.error("VNF Service Item not found in AAI response {}",
                            Serialization.gsonPretty.toJson(aaiResponseWrapper), e);
            return null;
        }

        try {
            tenantItem = aaiResponseWrapper.getAaiNqResponse().getInventoryResponseItems().get(0).getItems()
                            .getInventoryResponseItems().get(1);
        } catch (Exception e) {
            logger.error("Tenant Item not found in AAI response {}",
                            Serialization.gsonPretty.toJson(aaiResponseWrapper), e);
            return null;
        }

        // Find the index for base vf module and non-base vf module
        AaiNqInventoryResponseItem baseItem = findVfModule(aaiResponseWrapper, true);
        AaiNqInventoryResponseItem vfModuleItem = findVfModule(aaiResponseWrapper, false);

        // Report the error if either base vf module or non-base vf module is not found
        if (baseItem == null || vfModuleItem == null) {
            logger.error("Either base or non-base vf module is not found from AAI response.");
            return null;
        }

        // Construct SO Request for a policy's recipe
        if (RECIPE_VF_MODULE_CREATE.equals(policy.getRecipe())) {
            return constructCreateRequest(aaiResponseWrapper, policy, tenantItem, vnfItem, vnfServiceItem,
                    vfModuleItem);
        } else if (RECIPE_VF_MODULE_DELETE.equals(policy.getRecipe())) {
            return constructDeleteRequest(tenantItem, vnfItem, vnfServiceItem, vfModuleItem);
        } else {
            return null;
        }
    }

    /**
     * Construct SO request to create vf-module.
     *
     * @param aaiResponseWrapper the AAI response containing the VF modules
     * @param policy             the policy
     * @param tenantItem         tenant item from A&AI named-query response
     * @param vnfItem            vnf item from A&AI named-query response
     * @param vnfServiceItem     vnf service item from A&AI named-query response
     * @param vfModuleItem       vf module item from A&AI named-query response
     * @return SO create vf-module request
     */
    private SoRequest constructCreateRequest(AaiNqResponseWrapper aaiResponseWrapper, Policy policy,
                                             AaiNqInventoryResponseItem tenantItem, AaiNqInventoryResponseItem vnfItem,
                                             AaiNqInventoryResponseItem vnfServiceItem,
                                             AaiNqInventoryResponseItem vfModuleItem) {
        SoRequest request = new SoRequest();
        request.setOperationType(SoOperationType.SCALE_OUT);
        //
        //
        // Do NOT send So the requestId, they do not support this field
        //
        request.setRequestDetails(new SoRequestDetails());
        request.getRequestDetails().setRequestParameters(new SoRequestParameters());
        request.getRequestDetails().getRequestParameters().setUserParams(null);

        // cloudConfiguration
        request.getRequestDetails().setCloudConfiguration(constructCloudConfiguration(tenantItem));
        // modelInfo
        request.getRequestDetails().setModelInfo(constructVfModuleModelInfo(vfModuleItem));
        request.getRequestDetails().getModelInfo().setModelVersionId(vfModuleItem.getVfModule().getModelVersionId());

        // requestInfo
        request.getRequestDetails().setRequestInfo(constructRequestInfo());
        String vfModuleName = aaiResponseWrapper.genVfModuleName();
        request.getRequestDetails().getRequestInfo().setInstanceName(vfModuleName);

        // relatedInstanceList
        SoRelatedInstanceListElement relatedInstanceListElement1 = new SoRelatedInstanceListElement();
        SoRelatedInstanceListElement relatedInstanceListElement2 = new SoRelatedInstanceListElement();
        relatedInstanceListElement1.setRelatedInstance(new SoRelatedInstance());
        relatedInstanceListElement2.setRelatedInstance(new SoRelatedInstance());

        // Service Item
        relatedInstanceListElement1.getRelatedInstance()
                .setInstanceId(vnfServiceItem.getServiceInstance().getServiceInstanceId());
        relatedInstanceListElement1.getRelatedInstance().setModelInfo(new SoModelInfo());
        relatedInstanceListElement1.getRelatedInstance().getModelInfo().setModelType("service");
        relatedInstanceListElement1.getRelatedInstance().getModelInfo()
                .setModelInvariantId(vnfServiceItem.getServiceInstance().getModelInvariantId());
        for (AaiNqExtraProperty prop : vnfServiceItem.getExtraProperties().getExtraProperty()) {
            if (prop.getPropertyName().equals(MODEL_NAME_PROPERTY_KEY)) {
                relatedInstanceListElement1.getRelatedInstance().getModelInfo().setModelName(prop.getPropertyValue());
            } else if (prop.getPropertyName().equals(MODEL_VERSION_PROPERTY_KEY)) {
                relatedInstanceListElement1.getRelatedInstance().getModelInfo()
                        .setModelVersion(prop.getPropertyValue());
            } else if (prop.getPropertyName().equals(MODEL_VERSION_ID_PROPERTY_KEY)) {
                relatedInstanceListElement1.getRelatedInstance().getModelInfo()
                        .setModelVersionId(prop.getPropertyValue());
            }
        }

        // VNF Item
        relatedInstanceListElement2.getRelatedInstance().setInstanceId(vnfItem.getGenericVnf().getVnfId());
        relatedInstanceListElement2.getRelatedInstance().setModelInfo(new SoModelInfo());
        relatedInstanceListElement2.getRelatedInstance().getModelInfo().setModelType("vnf");
        relatedInstanceListElement2.getRelatedInstance().getModelInfo()
                .setModelInvariantId(vnfItem.getGenericVnf().getModelInvariantId());
        for (AaiNqExtraProperty prop : vnfItem.getExtraProperties().getExtraProperty()) {
            if (prop.getPropertyName().equals(MODEL_NAME_PROPERTY_KEY)) {
                relatedInstanceListElement2.getRelatedInstance().getModelInfo().setModelName(prop.getPropertyValue());
            } else if (prop.getPropertyName().equals(MODEL_VERSION_PROPERTY_KEY)) {
                relatedInstanceListElement2.getRelatedInstance().getModelInfo()
                        .setModelVersion(prop.getPropertyValue());
            } else if (prop.getPropertyName().equals(MODEL_VERSION_ID_PROPERTY_KEY)) {
                relatedInstanceListElement2.getRelatedInstance().getModelInfo()
                        .setModelVersionId(prop.getPropertyValue());
            }
        }
        relatedInstanceListElement2.getRelatedInstance().getModelInfo()
                .setModelCustomizationName(vnfItem.getGenericVnf().getVnfType()
                        .substring(vnfItem.getGenericVnf().getVnfType().lastIndexOf('/') + 1));
        relatedInstanceListElement2.getRelatedInstance().getModelInfo()
                .setModelCustomizationId(vnfItem.getGenericVnf().getModelCustomizationId());

        // Insert the Service Item and VNF Item
        request.getRequestDetails().getRelatedInstanceList().add(relatedInstanceListElement1);
        request.getRequestDetails().getRelatedInstanceList().add(relatedInstanceListElement2);

        // Request Parameters
        buildRequestParameters(policy, request.getRequestDetails());

        // Configuration Parameters
        buildConfigurationParameters(policy, request.getRequestDetails());
        // Save the instance IDs for the VNF and service to static fields
        // vfModuleId is not required for the create vf-module
        preserveInstanceIds(vnfItem.getGenericVnf().getVnfId(), vnfServiceItem.getServiceInstance()
                .getServiceInstanceId(), null);
        if (logger.isDebugEnabled()) {
            logger.debug("Constructed SO request: {}", Serialization.gsonPretty.toJson(request));
        }
        return request;
    }

    /**
     * Construct SO request to delete vf-module.
     *
     * @param tenantItem         tenant item from A&AI named-query response
     * @param vnfItem            vnf item from A&AI named-query response
     * @param vnfServiceItem     vnf service item from A&AI named-query response
     * @param vfModuleItem       vf module item from A&AI named-query response
     * @return SO delete vf-module request
     */
    private SoRequest constructDeleteRequest(AaiNqInventoryResponseItem tenantItem, AaiNqInventoryResponseItem
            vnfItem, AaiNqInventoryResponseItem vnfServiceItem, AaiNqInventoryResponseItem vfModuleItem) {
        SoRequest request = new SoRequest();
        request.setOperationType(SoOperationType.DELETE_VF_MODULE);
        request.setRequestDetails(new SoRequestDetails());
        request.getRequestDetails().setRelatedInstanceList(null);
        request.getRequestDetails().setConfigurationParameters(null);

        // cloudConfiguration
        request.getRequestDetails().setCloudConfiguration(constructCloudConfiguration(tenantItem));
        // modelInfo
        request.getRequestDetails().setModelInfo(constructVfModuleModelInfo(vfModuleItem));
        // requestInfo
        request.getRequestDetails().setRequestInfo(constructRequestInfo());
        // Save the instance IDs for the VNF, service and vfModule to static fields
        preserveInstanceIds(vnfItem.getGenericVnf().getVnfId(), vnfServiceItem.getServiceInstance()
                .getServiceInstanceId(), vfModuleItem.getVfModule().getVfModuleId());

        if (logger.isDebugEnabled()) {
            logger.debug("Constructed SO request: {}", Serialization.gsonPretty.toJson(request));
        }
        return request;
    }

    /**
     * Construct requestInfo for the SO requestDetails.
     *
     * @return SO request information
     */
    private SoRequestInfo constructRequestInfo() {
        SoRequestInfo soRequestInfo = new SoRequestInfo();
        soRequestInfo.setSource("POLICY");
        soRequestInfo.setSuppressRollback(false);
        soRequestInfo.setRequestorId("policy");
        return soRequestInfo;
    }

    /**
     * Construct modelInfo of the vfModule for the SO requestDetails.
     *
     * @param vfModuleItem vf module item from A&AI named-query response
     * @return SO Model info for the vfModule
     */
    private SoModelInfo constructVfModuleModelInfo(AaiNqInventoryResponseItem vfModuleItem) {
        SoModelInfo soModelInfo = new SoModelInfo();
        soModelInfo.setModelType("vfModule");
        soModelInfo.setModelInvariantId(vfModuleItem.getVfModule().getModelInvariantId());
        soModelInfo.setModelCustomizationId(vfModuleItem.getVfModule().getModelCustomizationId());

        for (AaiNqExtraProperty prop : vfModuleItem.getExtraProperties().getExtraProperty()) {
            if (prop.getPropertyName().equals(MODEL_NAME_PROPERTY_KEY)) {
                soModelInfo.setModelName(prop.getPropertyValue());
            } else if (prop.getPropertyName().equals(MODEL_VERSION_PROPERTY_KEY)) {
                soModelInfo.setModelVersion(prop.getPropertyValue());
            }
        }
        return soModelInfo;
    }

    /**
     * Construct cloudConfiguration for the SO requestDetails.
     *
     * @param tenantItem tenant item from A&AI named-query response
     * @return SO cloud configuration
     */
    private SoCloudConfiguration constructCloudConfiguration(AaiNqInventoryResponseItem tenantItem) {
        SoCloudConfiguration cloudConfiguration = new SoCloudConfiguration();
        cloudConfiguration.setTenantId(tenantItem.getTenant().getTenantId());
        cloudConfiguration.setLcpCloudRegionId(tenantItem.getItems().getInventoryResponseItems().get(0)
                .getCloudRegion().getCloudRegionId());
        return cloudConfiguration;
    }

    /**
     * This method is needed to get the serviceInstanceId and vnfInstanceId which is used
     * in the asyncSORestCall.
     *
     * @param requestId the request Id
     * @param wm the working memory
     * @param request the request
     */
    public static void sendRequest(String requestId, WorkingMemory wm, Object request) {
        SoManager soManager = new SoManager();
        soManager.asyncSoRestCall(requestId, wm, lastServiceItemServiceInstanceId, lastVNFItemVnfId,
                lastVfModuleItemVfModuleInstanceId, (SoRequest) request);
    }

    /**
     * Find the base or non base VF module item in an AAI response.
     * If there is more than one item, then the <i>last</i> item is returned
     *
     * @param aaiResponseWrapper the AAI response containing the VF modules
     * @param baseFlag true if we are searching for the base, false if we are searching
     *        for the non base
     * @return the base or non base VF module item or null if the module was not found
     */
    private AaiNqInventoryResponseItem findVfModule(AaiNqResponseWrapper aaiResponseWrapper, boolean baseFlag) {
        List<AaiNqInventoryResponseItem> lst = aaiResponseWrapper.getVfModuleItems(baseFlag);
        return (lst.isEmpty() ? null : lst.get(lst.size() - 1));
    }

    /**
     * Builds the request parameters from the policy payload.
     *
     * @param policy the policy
     * @param request request into which to stick the request parameters
     */
    private void buildRequestParameters(Policy policy, SoRequestDetails request) {
        // assume null until proven otherwise
        request.setRequestParameters(null);
        
        if (policy.getPayload() == null) {
            return;
        }

        String json = policy.getPayload().get(REQ_PARAM_NM);
        if (json == null) {
            return;
        }

        request.setRequestParameters(Serialization.gsonPretty.fromJson(json, SoRequestParameters.class));
    }

    /**
     * Builds the configuration parameters from the policy payload.
     *
     * @param policy the policy
     * @param request request into which to stick the configuration parameters
     */
    private void buildConfigurationParameters(Policy policy, SoRequestDetails request) {
        // assume null until proven otherwise
        request.setConfigurationParameters(null);
        
        if (policy.getPayload() == null) {
            return;
        }

        String json = policy.getPayload().get(CONFIG_PARAM_NM);
        if (json == null) {
            return;
        }

        request.setConfigurationParameters(Serialization.gsonPretty.fromJson(json, CONFIG_TYPE));
    }

    /**
     * This method is called to remember the last service instance ID, VNF Item VNF ID and vf module ID.
     * Note these fields are static, beware for multithreaded deployments
     *
     * @param vnfInstanceId update the last VNF instance ID to this value
     * @param serviceInstanceId update the last service instance ID to this value
     * @param vfModuleId update the vfModule instance ID to this value
     */
    private static void preserveInstanceIds(final String vnfInstanceId, final String serviceInstanceId,
                                            final String vfModuleId) {
        lastVNFItemVnfId = vnfInstanceId;
        lastServiceItemServiceInstanceId = serviceInstanceId;
        lastVfModuleItemVfModuleInstanceId = vfModuleId;
    }
}