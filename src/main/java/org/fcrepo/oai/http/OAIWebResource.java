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

package org.fcrepo.oai.http;

import static org.openarchives.oai._2.VerbType.IDENTIFY;
import static org.openarchives.oai._2.VerbType.LIST_METADATA_FORMATS;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBException;

import org.fcrepo.http.commons.session.InjectedSession;
import org.fcrepo.oai.service.OAIProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@Path("/oai")
public class OAIWebResource {

    @InjectedSession
    private Session session;

    @Autowired
    private OAIProviderService providerService;

    @GET
    @Produces(MediaType.TEXT_XML)
    public Object getOAIResponse(@QueryParam("verb") final String verb, @Context final UriInfo uriInfo)
            throws RepositoryException {
        if (verb.equals(IDENTIFY.value())) {
            return identifyRepository(uriInfo);
        } else if (verb.equals(LIST_METADATA_FORMATS.value())) {
            return metadataFormats(uriInfo);
        } else {
            throw new RepositoryException("Unable to create OAI response for verb '" + verb + "'");
        }
    }

    private Object metadataFormats(UriInfo uriInfo) throws RepositoryException {
        return providerService.listMetadataFormats(this.session, uriInfo);
    }

    private Object identifyRepository(final UriInfo uriInfo) throws RepositoryException {
        try {
            return providerService.identify(this.session, uriInfo);
        } catch (JAXBException e) {
            throw new RepositoryException(e);
        }
    }

}
