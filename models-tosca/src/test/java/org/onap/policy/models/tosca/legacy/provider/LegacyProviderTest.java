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

package org.onap.policy.models.tosca.legacy.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.models.base.PfModelException;
import org.onap.policy.models.dao.DaoParameters;
import org.onap.policy.models.dao.PfDao;
import org.onap.policy.models.dao.PfDaoFactory;
import org.onap.policy.models.dao.impl.DefaultPfDao;
import org.onap.policy.models.tosca.legacy.concepts.LegacyOperationalPolicy;

/**
 * Test the {@link LegacyProvider} class.
 *
 * @author Liam Fallon (liam.fallon@est.tech)
 */
public class LegacyProviderTest {
    private Connection connection;
    private PfDao pfDao;
    private Gson gson;


    /**
     * Set up the DAO towards the database.
     *
     * @throws Exception on database errors
     */
    @Before
    public void setupDao() throws Exception {
        // Use the JDBC UI "jdbc:h2:mem:testdb" to test towards the h2 database
        // Use the JDBC UI "jdbc:mariadb://localhost:3306/policy" to test towards a locally installed mariadb instance
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb", "policy", "P01icY");

        final DaoParameters daoParameters = new DaoParameters();
        daoParameters.setPluginClass(DefaultPfDao.class.getCanonicalName());

        // Use the persistence unit ToscaConceptTest to test towards the h2 database
        // Use the persistence unit ToscaConceptMariaDBTest to test towards a locally installed mariadb instance
        daoParameters.setPersistenceUnit("ToscaConceptTest");

        pfDao = new PfDaoFactory().createPfDao(daoParameters);
        pfDao.init(daoParameters);
    }

    /**
     * Set up GSON.
     */
    @Before
    public void setupGson() {
        gson = new Gson();
    }

    @After
    public void teardown() throws Exception {
        pfDao.close();
        connection.close();
    }

    @Test
    public void testPoliciesGet() throws PfModelException {
        try {
            new LegacyProvider().getOperationalPolicy(null, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().getOperationalPolicy(null, "");
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().getOperationalPolicy(pfDao, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("policyId is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().getOperationalPolicy(pfDao, "I Dont Exist");
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("no policy found for policy ID: I Dont Exist", exc.getMessage());
        }

        LegacyOperationalPolicy originalLop =
                gson.fromJson(ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.input.json"),
                        LegacyOperationalPolicy.class);

        assertNotNull(originalLop);

        LegacyOperationalPolicy createdLop = new LegacyProvider().createOperationalPolicy(pfDao, originalLop);

        assertEquals(originalLop, createdLop);

        LegacyOperationalPolicy gotLop = new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());

        assertEquals(gotLop, originalLop);

        String expectedJsonOutput = ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.output.json");
        String actualJsonOutput = gson.toJson(gotLop);

        assertEquals(actualJsonOutput.replaceAll("\\s+", ""), expectedJsonOutput.replaceAll("\\s+", ""));

        LegacyOperationalPolicy createdLopV2 = new LegacyProvider().createOperationalPolicy(pfDao, originalLop);
        LegacyOperationalPolicy gotLopV2 = new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());
        assertEquals(gotLopV2, createdLopV2);
    }

    @Test
    public void testPolicyCreate() throws PfModelException {
        try {
            new LegacyProvider().createOperationalPolicy(null, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().createOperationalPolicy(null, new LegacyOperationalPolicy());
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().createOperationalPolicy(pfDao, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("legacyOperationalPolicy is marked @NonNull but is null", exc.getMessage());
        }

        LegacyOperationalPolicy originalLop =
                gson.fromJson(ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.input.json"),
                        LegacyOperationalPolicy.class);

        assertNotNull(originalLop);

        LegacyOperationalPolicy createdLop = new LegacyProvider().createOperationalPolicy(pfDao, originalLop);

        assertEquals(originalLop, createdLop);

        LegacyOperationalPolicy gotLop = new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());

        assertEquals(gotLop, originalLop);

        String expectedJsonOutput = ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.output.json");
        String actualJsonOutput = gson.toJson(gotLop);

        assertEquals(actualJsonOutput.replaceAll("\\s+", ""), expectedJsonOutput.replaceAll("\\s+", ""));
    }


    @Test
    public void testPolicyUpdate() throws PfModelException {
        try {
            new LegacyProvider().updateOperationalPolicy(null, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().updateOperationalPolicy(null, new LegacyOperationalPolicy());
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().updateOperationalPolicy(pfDao, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("legacyOperationalPolicy is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().updateOperationalPolicy(pfDao, new LegacyOperationalPolicy());
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("no policy found for policy ID: null", exc.getMessage());
        }

        LegacyOperationalPolicy originalLop =
                gson.fromJson(ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.input.json"),
                        LegacyOperationalPolicy.class);

        assertNotNull(originalLop);

        LegacyOperationalPolicy createdLop = new LegacyProvider().createOperationalPolicy(pfDao, originalLop);
        assertEquals(originalLop, createdLop);

        LegacyOperationalPolicy gotLop = new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());
        assertEquals(gotLop, originalLop);

        originalLop.setContent("Some New Content");
        LegacyOperationalPolicy updatedLop = new LegacyProvider().updateOperationalPolicy(pfDao, originalLop);
        assertEquals(originalLop, updatedLop);

        LegacyOperationalPolicy gotUpdatedLop =
                new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());
        assertEquals(gotUpdatedLop, originalLop);
        assertEquals("Some New Content", gotUpdatedLop.getContent());
    }


    @Test
    public void testPoliciesDelete() throws PfModelException {
        try {
            new LegacyProvider().deleteOperationalPolicy(null, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().deleteOperationalPolicy(null, "");
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("dao is marked @NonNull but is null", exc.getMessage());
        }

        try {
            new LegacyProvider().deleteOperationalPolicy(pfDao, null);
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("policyId is marked @NonNull but is null", exc.getMessage());
        }


        try {
            new LegacyProvider().deleteOperationalPolicy(pfDao, "I Dont Exist");
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("no policy found for policy ID: I Dont Exist", exc.getMessage());
        }

        LegacyOperationalPolicy originalLop =
                gson.fromJson(ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.input.json"),
                        LegacyOperationalPolicy.class);

        assertNotNull(originalLop);

        LegacyOperationalPolicy createdLop = new LegacyProvider().createOperationalPolicy(pfDao, originalLop);
        assertEquals(originalLop, createdLop);

        LegacyOperationalPolicy gotLop = new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());

        assertEquals(gotLop, originalLop);

        String expectedJsonOutput = ResourceUtils.getResourceAsString("policies/vCPE.policy.operational.output.json");
        String actualJsonOutput = gson.toJson(gotLop);

        assertEquals(actualJsonOutput.replaceAll("\\s+", ""), expectedJsonOutput.replaceAll("\\s+", ""));

        LegacyOperationalPolicy deletedLop =
                new LegacyProvider().deleteOperationalPolicy(pfDao, originalLop.getPolicyId());
        assertEquals(deletedLop, originalLop);

        try {
            new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("no policy found for policy ID: operational.restart", exc.getMessage());
        }

        LegacyOperationalPolicy otherLop = new LegacyOperationalPolicy();
        otherLop.setPolicyId("another-policy");
        otherLop.setPolicyVersion("1");
        otherLop.setContent("content");

        LegacyOperationalPolicy createdOtherLop = new LegacyProvider().createOperationalPolicy(pfDao, otherLop);
        assertEquals(otherLop, createdOtherLop);

        try {
            new LegacyProvider().getOperationalPolicy(pfDao, originalLop.getPolicyId());
            fail("test should throw an exception here");
        } catch (Exception exc) {
            assertEquals("no policy found for policy ID: operational.restart", exc.getMessage());
        }

    }
}
