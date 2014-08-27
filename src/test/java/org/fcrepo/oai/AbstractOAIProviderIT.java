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

import static java.lang.Integer.parseInt;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.annotation.PostConstruct;
import javax.jcr.RepositoryException;
import javax.xml.bind.*;
import javax.xml.namespace.QName;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.runner.RunWith;
import org.openarchives.oai._2.IdentifyType;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.ObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/test-container.xml")
public abstract class AbstractOAIProviderIT {

    protected final Logger logger;

    protected final CloseableHttpClient client = HttpClients.createDefault();

    protected final ObjectFactory oaiFactory = new ObjectFactory();

    protected final Unmarshaller unmarshaller;

    protected final Marshaller marshaller;

    protected static final String serverAddress = "http://localhost:" +
            parseInt(System.getProperty("test.port", "8096"));

    @PostConstruct
    public void initTests() throws Exception {
        /* Check and/or add the default Identify response */
        if (!defaultIdentityResponseExists()) {
            IdentifyType id = oaiFactory.createIdentifyType();
            id.setRepositoryName("Fedora 4 Test Instance");
            id.setBaseURL(serverAddress);

            HttpPost post = new HttpPost(serverAddress + "/oai/identify/fcr:content");
            StringWriter data = new StringWriter();
            marshaller.marshal(new JAXBElement<IdentifyType>(new QName("Identify"), IdentifyType.class, id), data);
            post.setEntity(new StringEntity(data.toString()));
            try {
                HttpResponse resp = this.client.execute(post);
                assertEquals(201, resp.getStatusLine().getStatusCode());
            } finally {
                post.releaseConnection();
            }
        }
    }

    protected boolean defaultIdentityResponseExists() throws IOException {
        HttpGet get = new HttpGet(serverAddress + "/oai/identify/fcr:content");
        try {
            HttpResponse resp = this.client.execute(get);
            return resp.getStatusLine().getStatusCode() == 200;
        } finally {
            get.releaseConnection();
        }
    }

    public AbstractOAIProviderIT() {
        this.logger = LoggerFactory.getLogger(this.getClass());
        try {
            this.marshaller = JAXBContext.newInstance(IdentifyType.class).createMarshaller();
            this.unmarshaller = JAXBContext.newInstance(OAIPMHtype.class).createUnmarshaller();
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to create JAX-B context");
        }
    }

    @SuppressWarnings("unchecked")
    public HttpResponse getOAIPMHResponse(String verb, String identifier, String metadataPrefix) throws IOException,
            JAXBException {
        final StringBuilder url = new StringBuilder(serverAddress)
                .append("/oai?verb=")
                .append(verb);

        if (identifier != null && !identifier.isEmpty()) {
            url.append("&identifier=")
                    .append(identifier);
        }

        if (metadataPrefix != null && !metadataPrefix.isEmpty()) {
            url.append("&metadataPrefix=")
                    .append(metadataPrefix);
        }

        HttpGet get = new HttpGet(url.toString());
        return this.client.execute(get);
    }

    public void createFedoraObject(String id, String oaiRecordId) throws IOException {
        HttpPost post = new HttpPost(serverAddress + "/");
        post.addHeader("Slug",id);
        if (oaiRecordId != null) {
            StringBuilder sparql = new StringBuilder("INSERT {")
                    .append("<> ")
                    .append("<http://fedora.info/definitions/v4/config#hasOaiDCRecord> ")
                    .append("<info:fedora/").append(oaiRecordId).append(">")
                    .append("} WHERE {}");
            post.setEntity(new StringEntity(sparql.toString()));
            post.addHeader("Content-Type", "application/sparql-update");
        }
        HttpResponse resp = this.client.execute(post);
        assertEquals(201, resp.getStatusLine().getStatusCode());
        post.releaseConnection();
    }

    public void createOaiDcObject(String oaiDcId, InputStream src) throws IOException {
        HttpPost post = new HttpPost(serverAddress + "/");
        post.addHeader("Slug",oaiDcId);
        post.addHeader("Content-Type", "application/octet-stream");
        post.setEntity(new InputStreamEntity(src));
        HttpResponse resp = this.client.execute(post);
        assertEquals(201, resp.getStatusLine().getStatusCode());
        post.releaseConnection();

    }
}
