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

import org.fcrepo.http.commons.session.InjectedSession;
import org.openarchives.oai._2.*;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import java.util.Date;
import java.util.GregorianCalendar;

import static org.openarchives.oai._2.VerbType.*;

@Component
@Scope("prototype")
@Path("/oai")
public class OAIWebResource {

    @InjectedSession
    private Session session;

    private final ObjectFactory objFactory = new ObjectFactory();

    private final DatatypeFactory dataFactory;

    public OAIWebResource() throws DatatypeConfigurationException {
        this.dataFactory = DatatypeFactory.newInstance();
    }

    @GET
    @Produces(MediaType.TEXT_XML)
    public Object getOAIResponse(@QueryParam("verb") final String verb,
                               @QueryParam("metadataPrefix") final String mdPrefix,
                               @QueryParam("identifier") final String identifier,
                               @Context final UriInfo uriInfo)
            throws RepositoryException {
        if (verb.equals(IDENTIFY.value())) {
            return identifyRepository(uriInfo);
        } else {
            throw new RepositoryException("Unable to create OAI response for verb '" + verb + "'");
        }
    }

    private Object identifyRepository(final UriInfo uriInfo) throws RepositoryException {
        final IdentifyType id = objFactory.createIdentifyType();

        final RequestType req = objFactory.createRequestType();
        req.setVerb(VerbType.IDENTIFY);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final OAIPMHtype oai = objFactory.createOAIPMHtype();
        oai.setIdentify(id);
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(new GregorianCalendar()));
        oai.setRequest(req);
        return objFactory.createOAIPMH(oai);
    }

}
