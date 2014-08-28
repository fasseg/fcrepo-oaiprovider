/* 
 * Copyright 2014 Frank Asseg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */

package org.fcrepo.oai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.xml.bind.JAXBElement;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.junit.Test;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.VerbType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

public class ListIdentifiersIT extends AbstractOAIProviderIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testListIdentifyNoRecords() throws Exception {
        HttpResponse resp = getOAIPMHResponse(VerbType.LIST_IDENTIFIERS.value(), null, "oai_dc", "2014-01-02T20:30:00Z","2014-01-01T20:30:00Z");
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oaipmh = ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertNotNull(oaipmh.getRequest());
        assertEquals(VerbType.LIST_IDENTIFIERS.value(), oaipmh.getRequest().getVerb().value());
        assertEquals(1, oaipmh.getError().size());
        assertEquals(OAIPMHerrorcodeType.NO_RECORDS_MATCH, oaipmh.getError().get(0).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListIdentifyUnavailableMetadataFormat() throws Exception {
        HttpResponse resp = getOAIPMHResponse(VerbType.LIST_IDENTIFIERS.value(), null, "marc21", null, null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oaipmh = ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertNotNull(oaipmh.getRequest());
        assertEquals(VerbType.LIST_IDENTIFIERS.value(), oaipmh.getRequest().getVerb().value());
        assertEquals(1, oaipmh.getError().size());
        assertEquals(OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT, oaipmh.getError().get(0).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListIdentifyRecords() throws Exception {
        createFedoraObject("oai-test-" + RandomStringUtils.randomAlphabetic(16), "oai-dc-" + RandomStringUtils.randomAlphabetic(16));

        HttpResponse resp = getOAIPMHResponse(VerbType.LIST_IDENTIFIERS.value(), null, "oai_dc", null, null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oaipmh = ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(0, oaipmh.getError().size());
        assertNotNull(oaipmh.getListIdentifiers());
        assertNotNull(oaipmh.getListIdentifiers().getHeader());
        assertEquals(VerbType.LIST_IDENTIFIERS.value(), oaipmh.getRequest().getVerb().value());
        assertEquals(0, oaipmh.getError().size());
        assertTrue(oaipmh.getListIdentifiers().getHeader().size() > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListIdentifyRecordsFrom() throws Exception {
        createFedoraObject("oai-test-" + RandomStringUtils.randomAlphabetic(16), "oai-dc-" + RandomStringUtils.randomAlphabetic(16));

        HttpResponse resp = getOAIPMHResponse(VerbType.LIST_IDENTIFIERS.value(), null, "oai_dc", "2012-12-13T01:00:00Z", null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oaipmh = ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(0, oaipmh.getError().size());
        assertNotNull(oaipmh.getListIdentifiers());
        assertNotNull(oaipmh.getListIdentifiers().getHeader());
        assertEquals(VerbType.LIST_IDENTIFIERS.value(), oaipmh.getRequest().getVerb().value());
        assertEquals(0, oaipmh.getError().size());
        assertTrue(oaipmh.getListIdentifiers().getHeader().size() > 0);
    }
}
