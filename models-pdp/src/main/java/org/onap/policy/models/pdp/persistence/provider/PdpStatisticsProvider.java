/*-
 * ============LICENSE_START=======================================================
 * ONAP Policy Model
 * ================================================================================
 * Copyright (C) 2019-2020 Nordix Foundation.
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

package org.onap.policy.models.pdp.persistence.provider;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.NonNull;
import org.onap.policy.models.base.PfKey;
import org.onap.policy.models.base.PfModelException;
import org.onap.policy.models.base.PfModelRuntimeException;
import org.onap.policy.models.base.PfTimestampKey;
import org.onap.policy.models.base.PfValidationResult;
import org.onap.policy.models.dao.PfDao;
import org.onap.policy.models.pdp.concepts.PdpStatistics;
import org.onap.policy.models.pdp.persistence.concepts.JpaPdpStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class provides the provision of information on PAP concepts in the database to callers.
 *
 * @author Ning Xi (ning.xi@est.tech)
 */
public class PdpStatisticsProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(PdpStatisticsProvider.class);

    // Recurring string constants
    private static final String NOT_VALID = "\" is not valid \n";
    private static final String DESC_ORDER = "DESC";

    /**
     * Get PDP statistics.
     *
     * @param dao the DAO to use to access the database
     * @param name the name of the PDP statistics to get, null to get all PDPs
     * @return the PDP statistics found
     * @throws PfModelException on errors getting PDP statistics
     */
    public List<PdpStatistics> getPdpStatistics(@NonNull final PfDao dao, final String name, final Date timestamp)
            throws PfModelException {

        List<PdpStatistics> pdpStatistics = new ArrayList<>();
        if (name != null) {
            pdpStatistics
                    .add(dao.get(JpaPdpStatistics.class, new PfTimestampKey(name, PfKey.NULL_KEY_VERSION, timestamp))
                            .toAuthorative());
        } else {
            return asPdpStatisticsList(dao.getAll(JpaPdpStatistics.class));
        }
        return pdpStatistics;
    }

    /**
     * Get filtered PDP statistics.
     *
     * @param dao the DAO to use to access the database
     * @param name the pdpInstance name for the PDP statistics to get
     * @param pdpGroupName pdpGroupName to filter statistics
     * @param pdpSubGroup pdpSubGroupType name to filter statistics
     * @param startTimeStamp startTimeStamp to filter statistics
     * @param endTimeStamp endTimeStamp to filter statistics
     * @param sortOrder sortOrder to query database
     * @param getRecordNum Total query count from database
     * @return the PDP statistics found
     * @throws PfModelException on errors getting policies
     */
    public List<PdpStatistics> getFilteredPdpStatistics(@NonNull final PfDao dao, final String name,
            @NonNull final String pdpGroupName, final String pdpSubGroup, final Date startTimeStamp,
            final Date endTimeStamp, final String sortOrder, final int getRecordNum) {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("pdpGroupName", pdpGroupName);
        if (pdpSubGroup != null) {
            filterMap.put("pdpSubGroupName", pdpSubGroup);
        }

        return asPdpStatisticsList(dao.getFiltered(JpaPdpStatistics.class, name, PfKey.NULL_KEY_VERSION, startTimeStamp,
                endTimeStamp, filterMap, sortOrder, getRecordNum));
    }

    /**
     * Creates PDP statistics.
     *
     * @param dao the DAO to use to access the database
     * @param pdpStatisticsList a specification of the PDP statistics to create
     * @return the PDP statistics created
     * @throws PfModelException on errors creating PDP statistics
     */
    public List<PdpStatistics> createPdpStatistics(@NonNull final PfDao dao,
            @NonNull final List<PdpStatistics> pdpStatisticsList) throws PfModelException {

        for (PdpStatistics pdpStatistics : pdpStatisticsList) {
            JpaPdpStatistics jpaPdpStatistics = new JpaPdpStatistics();
            jpaPdpStatistics.fromAuthorative(pdpStatistics);

            PfValidationResult validationResult = jpaPdpStatistics.validate(new PfValidationResult());
            if (!validationResult.isOk()) {
                String errorMessage = "pdp statictics \"" + jpaPdpStatistics.getName() + NOT_VALID + validationResult;
                LOGGER.warn(errorMessage);
                throw new PfModelRuntimeException(Response.Status.BAD_REQUEST, errorMessage);
            }

            dao.create(jpaPdpStatistics);
        }

        // Return the created PDP statistics
        List<PdpStatistics> pdpStatistics = new ArrayList<>(pdpStatisticsList.size());

        for (PdpStatistics pdpStatisticsItem : pdpStatisticsList) {
            JpaPdpStatistics jpaPdpStatistics =
                    dao.get(JpaPdpStatistics.class, new PfTimestampKey(pdpStatisticsItem.getPdpInstanceId(),
                            PfKey.NULL_KEY_VERSION, pdpStatisticsItem.getTimeStamp()));
            pdpStatistics.add(jpaPdpStatistics.toAuthorative());
        }

        return pdpStatistics;
    }

    /**
     * Updates PDP statistics.
     *
     * @param dao the DAO to use to access the database
     * @param pdpStatisticsList a specification of the PDP statistics to update
     * @return the PDP statistics updated
     * @throws PfModelException on errors updating PDP statistics
     */
    public List<PdpStatistics> updatePdpStatistics(@NonNull final PfDao dao,
            @NonNull final List<PdpStatistics> pdpStatisticsList) throws PfModelException {

        for (PdpStatistics pdpStatistics : pdpStatisticsList) {
            JpaPdpStatistics jpaPdpStatistics = new JpaPdpStatistics();
            jpaPdpStatistics.fromAuthorative(pdpStatistics);

            PfValidationResult validationResult = jpaPdpStatistics.validate(new PfValidationResult());
            if (!validationResult.isOk()) {
                String errorMessage = "pdp statistics \"" + jpaPdpStatistics.getId() + NOT_VALID + validationResult;
                LOGGER.warn(errorMessage);
                throw new PfModelRuntimeException(Response.Status.BAD_REQUEST, errorMessage);
            }

            dao.update(jpaPdpStatistics);
        }

        // Return the created PDP statistics
        List<PdpStatistics> pdpStatistics = new ArrayList<>(pdpStatisticsList.size());

        for (PdpStatistics pdpStatisticsItem : pdpStatisticsList) {
            JpaPdpStatistics jpaPdpStatistics =
                    dao.get(JpaPdpStatistics.class, new PfTimestampKey(pdpStatisticsItem.getPdpInstanceId(),
                            PfKey.NULL_KEY_VERSION, pdpStatisticsItem.getTimeStamp()));
            pdpStatistics.add(jpaPdpStatistics.toAuthorative());
        }

        return pdpStatistics;
    }

    /**
     * Delete a PDP statistics.
     *
     * @param dao the DAO to use to access the database
     * @param name the name of the policy to get, null to get all PDP statistics
     * @param timestamp the timeStamp of statistics to delete, null to delete all statistics record of given PDP
     * @return the PDP statistics list deleted
     * @throws PfModelException on errors deleting PDP statistics
     */
    public List<PdpStatistics> deletePdpStatistics(@NonNull final PfDao dao, @NonNull final String name,
            final Date timestamp) {
        List<PdpStatistics> pdpStatisticsListToDel = asPdpStatisticsList(dao.getFiltered(JpaPdpStatistics.class, name,
                PfKey.NULL_KEY_VERSION, timestamp, timestamp, null, DESC_ORDER, 0));

        pdpStatisticsListToDel.stream().forEach(s -> dao.delete(JpaPdpStatistics.class,
                new PfTimestampKey(s.getPdpInstanceId(), PfKey.NULL_KEY_VERSION, s.getTimeStamp())));

        return pdpStatisticsListToDel;
    }

    /**
     * Convert JPA PDP statistics list to an PDP statistics list.
     *
     * @param jpaPdpStatisticsList the list to convert
     * @return the PDP statistics list
     */
    private List<PdpStatistics> asPdpStatisticsList(List<JpaPdpStatistics> jpaPdpStatisticsList) {
        return jpaPdpStatisticsList.stream().map(JpaPdpStatistics::toAuthorative).collect(Collectors.toList());
    }
}
