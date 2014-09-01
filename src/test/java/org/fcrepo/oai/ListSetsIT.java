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

import javax.xml.bind.*;
import javax.xml.namespace.QName;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.ObjectFactory;
import org.openarchives.oai._2.SetType;
import org.openarchives.oai._2.VerbType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ListSetsIT extends AbstractOAIProviderIT {

    private static Unmarshaller unmarshaller;

    private static Marshaller marshaller;

    @BeforeClass
    public static void setup() throws JAXBException {
        JAXBContext ctx = JAXBContext.newInstance(SetType.class);
        unmarshaller = ctx.createUnmarshaller();
        marshaller = ctx.createMarshaller();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateSet() throws Exception {
        final ObjectFactory fac = new ObjectFactory();
        SetType set = fac.createSetType();
        set.setSetName("My valuable Test set");
        set.setSetSpec("test" + RandomStringUtils.randomAlphabetic(8));
        HttpPost post = new HttpPost(serverAddress + "/oai/sets");
        post.setEntity(new InputStreamEntity(toStream(set), ContentType.TEXT_XML));
        HttpResponse resp = this.client.execute(post);
        assertEquals(201, resp.getStatusLine().getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateAndListSets() throws Exception {
        final ObjectFactory fac = new ObjectFactory();
        SetType set = fac.createSetType();
        set.setSetName("My valuable Test set");
        set.setSetSpec("test" + RandomStringUtils.randomAlphabetic(8));
        HttpPost post = new HttpPost(serverAddress + "/oai/sets");
        post.setEntity(new InputStreamEntity(toStream(set), ContentType.TEXT_XML));
        HttpResponse resp = this.client.execute(post);
        assertEquals(201, resp.getStatusLine().getStatusCode());

        resp = getOAIPMHResponse(VerbType.LIST_SETS.value(), null, null, null, null);
        OAIPMHtype oai = ((JAXBElement<OAIPMHtype>) this.unmarshaller.unmarshal(resp.getEntity().getContent())).getValue();
        assertEquals(200, resp.getStatusLine().getStatusCode());
        assertEquals(0, oai.getError().size() );
        assertNotNull(oai.getListSets());
        assertNotNull(oai.getListSets().getSet());
        assertTrue(oai.getListSets().getSet().size() > 0);
        assertNotNull(oai.getListSets().getSet().get(0).getSetName());
        assertNotNull(oai.getListSets().getSet().get(0).getSetSpec());
    }

    private static InputStream toStream(SetType set) throws JAXBException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        marshaller.marshal(new JAXBElement(new QName("set"), SetType.class, set), sink);
        return new ByteArrayInputStream(sink.toByteArray());
    }
}
