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

import java.io.InputStream;
import java.net.URI;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.fcrepo.oai.ResumptionToken;
import org.fcrepo.oai.service.OAIProviderService;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.VerbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;

@Scope("prototype")
@Path("/oai")
public class OAIWebResource {

    @Inject
    private Session session;

    @Autowired
    private OAIProviderService providerService;

    @POST
    @Path("/sets")
    @Consumes(MediaType.TEXT_XML)
    public Response createSet(@Context final UriInfo uriInfo, final InputStream src) throws RepositoryException {
        final String path = this.providerService.createSet(session, uriInfo, src);
        return Response.created(URI.create(path)).build();
    }

    @GET
    @Produces(MediaType.TEXT_XML)
    public Object getOAIResponse(
            @QueryParam("verb") String verb,
            @QueryParam("identifier") final String identifier,
            @QueryParam("metadataPrefix") String metadataPrefix,
            @QueryParam("from") String from,
            @QueryParam("until") String until,
            @QueryParam("set") String set,
            @QueryParam("resumptionToken") final String resumptionToken,
            @Context final UriInfo uriInfo) throws RepositoryException {
        int offset = 0;
        if (resumptionToken != null && !resumptionToken.isEmpty()) {
            try {
                final ResumptionToken token = OAIProviderService.decodeResumptionToken(resumptionToken);
                verb = token.getVerb();
                from = token.getFrom();
                until = token.getUntil();
                set = token.getSet();
                metadataPrefix = token.getMetadataPrefix();
                offset = token.getOffset();
            } catch (Exception e) {
                return providerService.error(null, null, null, OAIPMHerrorcodeType.BAD_RESUMPTION_TOKEN, "Resumption token is invalid");
            }
        }
        if (verb == null) {
            return providerService.error(null, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "Verb is required");
        }
        if (verb.equals(IDENTIFY.value())) {
            try {
                verifyEmpty(identifier, metadataPrefix, from, until, set);
            }catch(IllegalArgumentException e) {
                return providerService.error(VerbType.IDENTIFY, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, "Invalid arguments");
            }
            return identifyRepository(uriInfo);
        } else if (verb.equals(LIST_METADATA_FORMATS.value())) {
            try {
                verifyEmpty(from, until, set);
            }catch(IllegalArgumentException e) {
                return providerService.error(VerbType.LIST_METADATA_FORMATS, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, "Invalid arguments");
            }
            return metadataFormats(uriInfo, identifier);
        } else if (verb.equals(GET_RECORD.value())) {
            try {
                verifyEmpty(from, until, set);
            }catch(IllegalArgumentException e) {
                return providerService.error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, "Invalid arguments");
            }
            return getRecord(uriInfo, identifier, metadataPrefix);
        } else if (verb.equals(LIST_IDENTIFIERS.value())) {
            try {
                verifyEmpty(identifier);
            }catch(IllegalArgumentException e) {
                return providerService.error(VerbType.LIST_IDENTIFIERS, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, "Invalid arguments");
            }
            return listIdentifiers(uriInfo, metadataPrefix, from, until, set, offset);
        } else if (verb.equals(LIST_SETS.value())) {
            try {
                verifyEmpty(identifier);
            }catch(IllegalArgumentException e) {
                return providerService.error(VerbType.LIST_SETS, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, "Invalid arguments");
            }
            return listSets(uriInfo, offset);
        } else if (verb.equals(LIST_RECORDS.value())) {
            try {
                verifyEmpty(identifier);
            }catch(IllegalArgumentException e) {
                return providerService.error(VerbType.LIST_SETS, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, "Invalid arguments");
            }
            return listRecords(uriInfo, metadataPrefix, from, until, set, offset);
        } else {
            return providerService.error(null, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_VERB,
                    "The verb '" + verb + "' is invalid");
        }
    }

    private JAXBElement<OAIPMHtype> listRecords(UriInfo uriInfo, String metadataPrefix, String from, String until, String set, int offset) throws RepositoryException {
        return providerService.listRecords(this.session, uriInfo, metadataPrefix, from, until, set, offset);
    }

    private void verifyEmpty(String ... data) throws IllegalArgumentException{
        for (String s:data) {
            if (s != null && !s.isEmpty())  {
                throw new IllegalArgumentException("Wrong argument for method");
            }
        }
    }

    private JAXBElement<OAIPMHtype> listSets(UriInfo uriInfo, int offset) throws RepositoryException {
        return providerService.listSets(session, uriInfo, offset);
    }

    private JAXBElement<OAIPMHtype> listIdentifiers(UriInfo uriInfo, String metadataPrefix, String from,
            String until, String set, int offset) throws RepositoryException {
        return providerService.listIdentifiers(this.session, uriInfo, metadataPrefix, from, until, set, offset);
    }

    private Object getRecord(final UriInfo uriInfo, final String identifier, final String metadataPrefix)
            throws RepositoryException {
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
