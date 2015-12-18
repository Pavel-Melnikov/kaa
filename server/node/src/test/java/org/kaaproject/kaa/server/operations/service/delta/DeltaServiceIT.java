/*
 * Copyright 2014 CyberVision, Inc.
 *
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
 */

package org.kaaproject.kaa.server.operations.service.delta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericRecord;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kaaproject.kaa.common.avro.GenericAvroConverter;
import org.kaaproject.kaa.common.dto.ApplicationDto;
import org.kaaproject.kaa.common.dto.ConfigurationDto;
import org.kaaproject.kaa.common.dto.ConfigurationSchemaDto;
import org.kaaproject.kaa.common.dto.EndpointConfigurationDto;
import org.kaaproject.kaa.common.dto.EndpointGroupDto;
import org.kaaproject.kaa.common.dto.EndpointGroupStateDto;
import org.kaaproject.kaa.common.dto.EndpointProfileDto;
import org.kaaproject.kaa.common.dto.ProfileFilterDto;
import org.kaaproject.kaa.common.dto.ProfileSchemaDto;
import org.kaaproject.kaa.common.dto.TenantDto;
import org.kaaproject.kaa.common.endpoint.gen.BasicEndpointProfile;
import org.kaaproject.kaa.common.hash.EndpointObjectHash;
import org.kaaproject.kaa.server.common.core.algorithms.delta.DeltaCalculatorException;
import org.kaaproject.kaa.server.common.dao.ApplicationService;
import org.kaaproject.kaa.server.common.dao.ConfigurationService;
import org.kaaproject.kaa.server.common.dao.EndpointService;
import org.kaaproject.kaa.server.common.dao.ProfileService;
import org.kaaproject.kaa.server.common.dao.UserService;
import org.kaaproject.kaa.server.common.dao.exception.IncorrectParameterException;
import org.kaaproject.kaa.server.common.nosql.mongo.dao.MongoDBTestRunner;
import org.kaaproject.kaa.server.operations.pojo.GetDeltaRequest;
import org.kaaproject.kaa.server.operations.pojo.GetDeltaResponse;
import org.kaaproject.kaa.server.operations.service.OperationsServiceIT;
import org.kaaproject.kaa.server.operations.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/operations/common-test-context.xml")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
@ActiveProfiles(value = "mongo")
public class DeltaServiceIT {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    protected static final Logger LOG = LoggerFactory.getLogger(DeltaServiceIT.class);

    private static final int PROFILE_VERSION = 1;
    private static final int PROFILE_SCHEMA_VERSION = 1;
    private static final int OLD_ENDPOINT_SEQ_NUMBER = 0;
    private static final int NEW_APPLICATION_SEQ_NUMBER = 6;
    private static final int MAJOR_VERSION = 1;

    private static final int CONF_SCHEMA_VERSION = 2;

    private static final String CUSTOMER_ID = "CustomerId";
    private static final String APPLICATION_ID = "ApplicationId";
    private static final String APPLICATION_NAME = "ApplicationName";
    private static final GenericAvroConverter<GenericRecord> avroConverter = new GenericAvroConverter<>(BasicEndpointProfile.SCHEMA$);
    private static final BasicEndpointProfile ENDPOINT_PROFILE = new BasicEndpointProfile("dummy profile 1");
    private static byte[] PROFILE_BYTES;
    private static String PROFILE_JSON;

    private static final byte[] ENDPOINT_KEY = "EndpointKey".getBytes(UTF_8);

    @Autowired
    protected DeltaService deltaService;

    @Autowired
    protected UserService userService;

    @Autowired
    protected ApplicationService applicationService;

    @Autowired
    protected EndpointService endpointService;

    @Autowired
    protected ConfigurationService configurationService;

    @Autowired
    protected ProfileService profileService;

    @Autowired
    protected CacheService cacheService;

    @Autowired
    protected ConfigurationService confService;

    private TenantDto tenant;
    private ApplicationDto application;
    private ProfileSchemaDto profileSchema;
    private ProfileFilterDto profileFilter;
    private EndpointProfileDto endpointProfile;
    private EndpointConfigurationDto endpointConfiguration;
    private byte[] endpointConfigurationBytes;
    private ConfigurationSchemaDto confSchema;

    private String egAllId;
    private String pfAllId;
    private String cfAllId;

    @BeforeClass
    public static void init() throws Exception {
        MongoDBTestRunner.setUp();
    }

    @AfterClass
    public static void after() throws Exception {
        MongoDBTestRunner.getDB().dropDatabase();
        MongoDBTestRunner.tearDown();
    }

    @Before
    public void beforeTest() throws IOException, DeltaCalculatorException {
        String dataSchema = OperationsServiceIT.getResourceAsString(OperationsServiceIT.DATA_SCHEMA_LOCATION);
        PROFILE_BYTES = avroConverter.encode(ENDPOINT_PROFILE);
        PROFILE_JSON = avroConverter.encodeToJson(ENDPOINT_PROFILE);

        tenant = new TenantDto();
        tenant.setName(CUSTOMER_ID);
        tenant = userService.saveTenant(tenant);
        assertNotNull(tenant);
        assertNotNull(tenant.getId());

        ApplicationDto applicationDto = new ApplicationDto();
        applicationDto.setTenantId(tenant.getId());
        applicationDto.setApplicationToken(APPLICATION_ID);
        applicationDto.setName(APPLICATION_NAME);
        applicationDto.setSequenceNumber(NEW_APPLICATION_SEQ_NUMBER);
        applicationDto = applicationService.saveApp(applicationDto);
        assertNotNull(applicationDto);
        assertNotNull(applicationDto.getId());

        application = applicationService.findAppById(applicationDto.getId());

        EndpointGroupDto groupAll = endpointService.findEndpointGroupsByAppId(application.getId()).get(0);

        ProfileSchemaDto profileSchemaObj = new ProfileSchemaDto();
        profileSchemaObj.setMajorVersion(PROFILE_SCHEMA_VERSION);
        profileSchemaObj.setMinorVersion(0);
        profileSchemaObj.setSchema(BasicEndpointProfile.SCHEMA$.toString());
        profileSchemaObj.setApplicationId(application.getId());
        ProfileSchemaDto profileSchemaDto = profileService.saveProfileSchema(profileSchemaObj);

        profileSchema = profileService.findProfileSchemaById(profileSchemaDto.getId());

        EndpointGroupDto endpointGroup = new EndpointGroupDto();
        endpointGroup.setApplicationId(application.getId());
        endpointGroup.setName("Test group");
        endpointGroup.setWeight(277);
        endpointGroup.setDescription("Test Description");
        endpointGroup = endpointService.saveEndpointGroup(endpointGroup);

        ProfileFilterDto profileFilterObj = new ProfileFilterDto();
        profileFilterObj.setApplicationId(application.getId());
        profileFilterObj.setEndpointGroupId(endpointGroup.getId());
        profileFilterObj.setBody("profileBody.contains(\"dummy\")");
        profileFilterObj.setSchemaId(profileSchema.getId());
        profileFilter = profileService.saveProfileFilter(profileFilterObj);
        profileService.activateProfileFilter(profileFilter.getId(), null);

        confSchema = new ConfigurationSchemaDto();
        confSchema.setApplicationId(application.getId());
        confSchema.setMajorVersion(CONF_SCHEMA_VERSION);
        confSchema.setSchema(dataSchema);
        try {
            confSchema = configurationService.saveConfSchema(confSchema);
        } catch (IncorrectParameterException e) {
            Assert.fail("Can't generate schemas");
        }
        Assert.assertNotNull(confSchema);
        Assert.assertNotNull(confSchema.getId());

        egAllId = groupAll.getId();
        pfAllId = profileFilter.getId();
        ConfigurationDto confDto = configurationService.findConfigurationByEndpointGroupIdAndVersion(egAllId, CONF_SCHEMA_VERSION);
        cfAllId = confDto.getId();

        endpointConfiguration = new EndpointConfigurationDto();
        endpointConfiguration.setConfiguration(confDto.getBody().getBytes(UTF_8));
        endpointConfiguration.setConfigurationHash(EndpointObjectHash.fromSHA1(confDto.getBody()).getData());
        endpointConfiguration = endpointService.saveEndpointConfiguration(endpointConfiguration);
        assertNotNull(endpointConfiguration);

        EndpointGroupStateDto egs = new EndpointGroupStateDto();
        egs.setConfigurationId(cfAllId);
        egs.setEndpointGroupId(egAllId);
        egs.setProfileFilterId(pfAllId);

        endpointProfile = new EndpointProfileDto();
        endpointProfile.setEndpointKeyHash(UUID.randomUUID().toString().getBytes());
        endpointProfile.setProfile(PROFILE_JSON);
        endpointProfile.setProfileHash(EndpointObjectHash.fromSHA1(PROFILE_BYTES).getData());
        endpointProfile.setConfigurationHash(endpointConfiguration.getConfigurationHash());
        endpointProfile.setConfigurationVersion(CONF_SCHEMA_VERSION);
        endpointProfile.setProfileVersion(PROFILE_VERSION);
        endpointProfile.setCfGroupStates(Collections.singletonList(egs));
        endpointProfile.setNfGroupStates(Collections.singletonList(egs));
        endpointProfile = endpointService.saveEndpointProfile(endpointProfile);
        assertNotNull(endpointProfile);
        assertNotNull(endpointProfile.getId());
    }

    @After
    public void afterTest() {
        MongoDBTestRunner.getDB().dropDatabase();
    }

    @Test
    public void testDeltaServiceNoHistoryDelta() throws Exception {
        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), EndpointObjectHash.fromSHA1(endpointConfiguration
                .getConfiguration()), OLD_ENDPOINT_SEQ_NUMBER);
        request.setEndpointProfile(endpointProfile);
        HistoryDelta historyDelta = new HistoryDelta(new ArrayList<EndpointGroupStateDto>(), false, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.NO_DELTA, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNull(response.getDelta());
        assertNull(response.getConfSchema());
    }

    @Test
    @Ignore("Kaa #7786")
    public void testDeltaServiceNoHistoryDeltaFetchSchema() throws Exception {
        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), EndpointObjectHash.fromSHA1(endpointConfiguration
                .getConfiguration()), OLD_ENDPOINT_SEQ_NUMBER);
        request.setEndpointProfile(endpointProfile);
        request.setFetchSchema(true);
        HistoryDelta historyDelta = new HistoryDelta(new ArrayList<EndpointGroupStateDto>(), false, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.NO_DELTA, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNull(response.getDelta());
        assertNotNull(response.getConfSchema());
    }

    @Test
    public void testDeltaServiceFirstRequest() throws Exception {
        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), OLD_ENDPOINT_SEQ_NUMBER);
        request.setEndpointProfile(endpointProfile);
        List<EndpointGroupStateDto> changes = new ArrayList<>();
        changes.add(new EndpointGroupStateDto(egAllId, pfAllId, cfAllId));
        HistoryDelta historyDelta = new HistoryDelta(changes, true, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        endpointConfiguration.setConfigurationHash(EndpointObjectHash.fromSHA1(endpointConfiguration.getConfiguration()).getData());

        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.CONF_RESYNC, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNotNull(response.getDelta());
        endpointConfigurationBytes = response.getDelta().getData();
        assertNotNull(endpointConfigurationBytes);
    }
    
    @Test
    public void testDeltaServiceFirstRequestResync() throws Exception {
        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), OLD_ENDPOINT_SEQ_NUMBER, true);
        request.setEndpointProfile(endpointProfile);
        List<EndpointGroupStateDto> changes = new ArrayList<>();
        changes.add(new EndpointGroupStateDto(egAllId, pfAllId, cfAllId));
        HistoryDelta historyDelta = new HistoryDelta(changes, true, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        endpointConfiguration.setConfigurationHash(EndpointObjectHash.fromSHA1(endpointConfiguration.getConfiguration()).getData());

        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.CONF_RESYNC, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNotNull(response.getDelta());
        endpointConfigurationBytes = response.getDelta().getData();
        assertNotNull(endpointConfigurationBytes);
        GenericAvroConverter<GenericContainer> converter = new GenericAvroConverter<GenericContainer>(confSchema.getBaseSchema());
        GenericContainer container = converter.decodeBinary(endpointConfigurationBytes);
        assertNotNull(container);
        LOG.info("decoded data {}", container.toString());
    }

    @Test
    public void testDeltaServiceHashMismatch() throws Exception {
        byte[] wrongConf = Arrays.copyOf(endpointConfiguration.getConfiguration(), endpointConfiguration.getConfiguration().length);
        wrongConf[0] = (byte) (wrongConf[0] + 1);
        EndpointObjectHash newConfHash = EndpointObjectHash.fromSHA1(wrongConf);
        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), newConfHash, OLD_ENDPOINT_SEQ_NUMBER);

        request.setEndpointProfile(endpointProfile);
        List<EndpointGroupStateDto> changes = new ArrayList<>();
        changes.add(new EndpointGroupStateDto(egAllId, pfAllId, cfAllId));
        HistoryDelta historyDelta = new HistoryDelta(changes, true, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.CONF_RESYNC, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNotNull(response.getDelta());
        assertNotNull(response.getDelta().getData());
    }

    @Test
    public void testDeltaServiceSecondRequest() throws Exception {
        GenericAvroConverter<GenericContainer> newConfConverter = new GenericAvroConverter<>(new Schema.Parser().parse(confSchema
                .getBaseSchema()));
        GenericContainer container = newConfConverter.decodeJson(OperationsServiceIT
                .getResourceAsString(OperationsServiceIT.BASE_DATA_UPDATED_LOCATION));
        byte[] newConfData = newConfConverter.encodeToJsonBytes(container);

        ConfigurationDto newConfDto = new ConfigurationDto();
        newConfDto.setEndpointGroupId(egAllId);
        newConfDto.setSchemaId(confSchema.getId());
        newConfDto.setBody(new String(newConfData, UTF_8));

        newConfDto = configurationService.saveConfiguration(newConfDto);
        configurationService.activateConfiguration(newConfDto.getId(), "test");

        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), EndpointObjectHash.fromSHA1(endpointConfiguration
                .getConfiguration()), OLD_ENDPOINT_SEQ_NUMBER);

        request.setEndpointProfile(endpointProfile);
        List<EndpointGroupStateDto> changes = new ArrayList<>();
        changes.add(new EndpointGroupStateDto(egAllId, pfAllId, newConfDto.getId()));
        HistoryDelta historyDelta = new HistoryDelta(changes, true, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.DELTA, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNotNull(response.getDelta());
        assertNotNull(response.getDelta().getData());
    }

    @Test
    public void testDeltaServiceSecondRequestNoChanges() throws Exception {
        GetDeltaRequest request = new GetDeltaRequest(application.getApplicationToken(), EndpointObjectHash.fromSHA1(endpointConfiguration
                .getConfiguration()), OLD_ENDPOINT_SEQ_NUMBER);
        endpointProfile.setConfigurationHash(EndpointObjectHash.fromSHA1(endpointConfiguration.getConfiguration()).getData());
        request.setEndpointProfile(endpointProfile);
        List<EndpointGroupStateDto> changes = new ArrayList<>();
        changes.add(new EndpointGroupStateDto(egAllId, pfAllId, cfAllId));
        HistoryDelta historyDelta = new HistoryDelta(changes, true, false);
        GetDeltaResponse response = deltaService.getDelta(request, historyDelta, NEW_APPLICATION_SEQ_NUMBER);
        assertNotNull(response);
        assertEquals(GetDeltaResponse.GetDeltaResponseType.NO_DELTA, response.getResponseType());
        assertEquals(NEW_APPLICATION_SEQ_NUMBER, response.getSequenceNumber());
        assertNull(response.getDelta());
    }
}