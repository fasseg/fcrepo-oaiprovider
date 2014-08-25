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

package org.fcrepo.oai.service;

import java.io.InputStream;
import java.util.GregorianCalendar;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.transform.stream.StreamSource;

import com.hp.hpl.jena.rdf.model.Resource;
import org.fcrepo.http.commons.session.SessionFactory;
import org.fcrepo.kernel.Datastream;
import org.fcrepo.kernel.impl.rdf.impl.DefaultIdentifierTranslator;
import org.fcrepo.kernel.rdf.IdentifierTranslator;
import org.fcrepo.kernel.services.DatastreamService;
import org.fcrepo.kernel.services.NodeService;
import org.fcrepo.kernel.services.ObjectService;
import org.fcrepo.oai.MetadataFormat;
import org.openarchives.oai._2.*;
import org.springframework.beans.factory.annotation.Autowired;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

public class OAIProviderService {

    private final ObjectFactory objFactory;

    private final DatatypeFactory dataFactory;

    private final Unmarshaller unmarshaller;

    private final IdentifierTranslator subjectTranslator = new DefaultIdentifierTranslator();

    private final Model rdfModel = ModelFactory.createDefaultModel();

    private String identifyUri;

    private List<MetadataFormat> metadataFormats;

    @Autowired
    private DatastreamService datastreamService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private ObjectService objectService;

    public void setIdentifyUri(String identifyUri) {
        this.identifyUri = identifyUri;
    }

    public OAIProviderService() throws DatatypeConfigurationException, JAXBException {
        this.dataFactory = DatatypeFactory.newInstance();
        this.objFactory = new ObjectFactory();
        this.unmarshaller = JAXBContext.newInstance(OAIPMHtype.class, IdentifyType.class).createUnmarshaller();
    }

    public JAXBElement<OAIPMHtype> identify(final Session session, UriInfo uriInfo) throws RepositoryException,
            JAXBException {
        final String path = subjectTranslator.getPathFromSubject(rdfModel.createResource(identifyUri));
        final Datastream ds = this.datastreamService.getDatastream(session, path);
        final InputStream data = ds.getContent();
        final IdentifyType id = this.unmarshaller.unmarshal(new StreamSource(data), IdentifyType.class).getValue();

        final RequestType req = objFactory.createRequestType();
        req.setVerb(VerbType.IDENTIFY);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final OAIPMHtype oai = objFactory.createOAIPMHtype();
        oai.setIdentify(id);
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(new GregorianCalendar()));
        oai.setRequest(req);
        return objFactory.createOAIPMH(oai);
    }

    public JAXBElement<OAIPMHtype> listMetadataFormats(Session session, UriInfo uriInfo) throws RepositoryException {
        final String path = createSubject(uriInfo);
        final ListMetadataFormatsType listMetadataFormats = objFactory.createListMetadataFormatsType();
        for (MetadataFormat mdf : metadataFormats) {
            final MetadataFormatType mdft = objFactory.createMetadataFormatType();
            mdft.setMetadataPrefix(mdf.getPrefix());
            mdft.setMetadataNamespace(mdf.getNamespace());
            mdft.setSchema(mdf.getSchemaUrl());
            listMetadataFormats.getMetadataFormat().add(mdft);
        }

        final RequestType req = objFactory.createRequestType();
        req.setVerb(VerbType.LIST_METADATA_FORMATS);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final OAIPMHtype oai = objFactory.createOAIPMHtype();
        oai.setListMetadataFormats(listMetadataFormats);
        oai.setRequest(req);
        return objFactory.createOAIPMH(oai);
    }

    public void setMetadataFormats(List<MetadataFormat> metadataFormats) {
        this.metadataFormats = metadataFormats;
    }

    public String createSubject(UriInfo uriInfo) throws RepositoryException {
        return subjectTranslator.getPathFromSubject(rdfModel.createResource(uriInfo.getRequestUri().toASCIIString()));
    }
}
