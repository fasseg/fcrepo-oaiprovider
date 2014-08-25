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

import org.apache.http.HttpResponse;
import org.junit.Test;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.VerbType;

import javax.xml.bind.JAXBElement;

public class GetRecordIT extends AbstractOAIProviderIT{
    @Test
    public void testGetNonExistingObjectRecord() throws Exception {
        HttpResponse resp = getOAIPMHResponse(VerbType.GET_RECORD.value(), "non-existing-id", "oai_dc");
        assertEquals(200, resp.getStatusLine().getStatusCode());
        OAIPMHtype oai = ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(1, oai.getError().size());
        assertEquals(OAIPMHerrorcodeType.ID_DOES_NOT_EXIST, oai.getError().get(0).getCode());
    }

}
