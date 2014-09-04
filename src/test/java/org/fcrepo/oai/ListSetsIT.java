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

import static org.junit.Assert.*;

import javax.xml.bind.JAXBElement;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.junit.Test;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.VerbType;

public class ListSetsIT extends AbstractOAIProviderIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateSet() throws Exception {
        createSet("oai-test-set-" + RandomStringUtils.randomAlphabetic(16), null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateAndListSets() throws Exception {
        createSet("oai-test-set-" + RandomStringUtils.randomAlphabetic(16), null);
        HttpResponse resp = getOAIPMHResponse(VerbType.LIST_SETS.value(), null, null, null, null, null);
        OAIPMHtype oai =
                ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(200, resp.getStatusLine().getStatusCode());
        assertEquals(0, oai.getError().size());
        assertNotNull(oai.getListSets());
        assertNotNull(oai.getListSets().getSet());
        assertTrue(oai.getListSets().getSet().size() > 0);
        assertNotNull(oai.getListSets().getSet().get(0).getSetName());
        assertNotNull(oai.getListSets().getSet().get(0).getSetSpec());
    }
}
