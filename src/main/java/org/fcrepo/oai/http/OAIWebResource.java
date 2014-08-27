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

import static org.openarchives.oai._2.VerbType.*;

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
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.VerbType;
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
    public Object getOAIResponse(
            @QueryParam("verb") final String verb,
            @QueryParam("identifier") final String identifier,
            @QueryParam("metadataPrefix") final String metadataPrefix,
            @QueryParam("from") final String from,
            @QueryParam("until") final String until,
            @QueryParam("set") final String set,

            @Context final UriInfo uriInfo) throws RepositoryException {

        if (verb.equals(IDENTIFY.value())) {
            return identifyRepository(uriInfo);
        } else if (verb.equals(LIST_METADATA_FORMATS.value())) {
            return metadataFormats(uriInfo, identifier);
        } else if (verb.equals(GET_RECORD.value())) {
            return getRecord(uriInfo, identifier, metadataPrefix);
        } else {
            return providerService.error(null, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_VERB, "The verb '" + verb + "' is invalid");
        }
    }

    private Object getRecord(final UriInfo uriInfo, final String identifier, final String metadataPrefix) throws RepositoryException {
        return providerService.getRecord(this.session, uriInfo, identifier, metadataPrefix);
    }


    private Object metadataFormats(UriInfo uriInfo, final String identifier) throws RepositoryException {
        return providerService.listMetadataFormats(this.session, uriInfo, identifier);
    }

    private Object identifyRepository(final UriInfo uriInfo) throws RepositoryException {
        try {
            return providerService.identify(this.session, uriInfo);
        } catch (JAXBException e) {
            throw new RepositoryException(e);
        }
    }

}
