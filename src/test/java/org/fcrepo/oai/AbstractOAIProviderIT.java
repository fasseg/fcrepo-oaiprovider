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

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.runner.RunWith;
import org.openarchives.oai._2.OAIPMHtype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/test-container.xml")
public abstract class AbstractOAIProviderIT {

    protected final Logger logger;

    protected final CloseableHttpClient client = HttpClients.createDefault();

    protected final Unmarshaller unmarshaller;

    protected static final String serverAddress = "http://localhost:" +
            parseInt(System.getProperty("test.port", "8096")) + "/";

    public AbstractOAIProviderIT(){
        this.logger = LoggerFactory.getLogger(this.getClass());
        try {
            this.unmarshaller = JAXBContext.newInstance(OAIPMHtype.class).createUnmarshaller();
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to create JAX-B context");
        }
    }
}
