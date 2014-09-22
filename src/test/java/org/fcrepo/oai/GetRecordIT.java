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

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.junit.Test;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.VerbType;

public class GetRecordIT extends AbstractOAIProviderIT {

    @Test
    public void testGetNonExistingObjectRecord() throws Exception {
        HttpResponse resp =
                getOAIPMHResponse(VerbType.GET_RECORD.value(), "non-existing-id", "oai_dc", null, null, null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oai =
                ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(1, oai.getError().size());
        assertEquals(OAIPMHerrorcodeType.ID_DOES_NOT_EXIST, oai.getError().get(0).getCode());
    }

    @Test
    public void testGetNonExistingOAIDCRecord() throws Exception {
        String objId = "oai-test-" + RandomStringUtils.randomAlphabetic(8);
        createFedoraObject(objId);
        HttpResponse resp = getOAIPMHResponse(VerbType.GET_RECORD.value(), objId, "oai_dc", null, null, null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oai =
                ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(1, oai.getError().size());
        assertEquals(OAIPMHerrorcodeType.NO_RECORDS_MATCH, oai.getError().get(0).getCode());
    }

    @Test
    public void testGetOAIDCDatastream() throws Exception {
        String objId = "oai-test-" + RandomStringUtils.randomAlphabetic(8);
        String oaiDcId = "oai-dc-" + RandomStringUtils.randomAlphabetic(8);
        createFedoraObjectWithOaiRecord(objId, oaiDcId, null, this.getClass().getClassLoader().getResourceAsStream(
                "test-data/oaidc.xml"));
        HttpResponse resp = getOAIPMHResponse(VerbType.GET_RECORD.value(), objId, "oai_dc", null, null, null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oai =
                ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(0, oai.getError().size());
        assertNotNull(oai.getGetRecord().getRecord().getMetadata().getAny());
        assertEquals(objId, oai.getGetRecord().getRecord().getHeader().getIdentifier());
        this.marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_ENCODING, "utf-8");
        this.marshaller.setProperty("com.sun.xml.bind.characterEscapeHandler", NOOPEscapeHandler.getInstance());
        this.marshaller.marshal(new JAXBElement<OAIPMHtype>(new QName("http://www.openarchives.org/OAI/2.0/oai_dc/", "oai_dc"), OAIPMHtype.class, oai), System.out);
    }
}
