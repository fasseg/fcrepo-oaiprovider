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

import org.apache.http.HttpResponse;
import org.junit.Test;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.VerbType;

public class IdentifyIT extends AbstractOAIProviderIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testIdentify() throws Exception {
        HttpResponse resp = getOAIPMHResponse(VerbType.IDENTIFY.value(), null, null, null, null, null);
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oaipmh =
                ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(0, oaipmh.getError().size());
        assertNotNull(oaipmh.getIdentify());
        assertNotNull(oaipmh.getRequest());
        assertEquals(VerbType.IDENTIFY.value(), oaipmh.getRequest().getVerb().value());
        assertEquals("Fedora 4 Test Instance", oaipmh.getIdentify().getRepositoryName());
        assertEquals(serverAddress, oaipmh.getIdentify().getBaseURL());
    }
}
