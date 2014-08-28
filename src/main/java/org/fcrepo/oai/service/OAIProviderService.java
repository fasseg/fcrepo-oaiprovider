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

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.fcrepo.http.commons.session.SessionFactory;
import org.fcrepo.kernel.Datastream;
import org.fcrepo.kernel.FedoraObject;
import org.fcrepo.kernel.RdfLexicon;
import org.fcrepo.kernel.impl.rdf.impl.DefaultIdentifierTranslator;
import org.fcrepo.kernel.rdf.IdentifierTranslator;
import org.fcrepo.kernel.services.DatastreamService;
import org.fcrepo.kernel.services.NodeService;
import org.fcrepo.kernel.services.ObjectService;
import org.fcrepo.oai.MetadataFormat;
import org.fcrepo.transform.sparql.JQLConverter;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.openarchives.oai._2.*;
import org.springframework.beans.factory.annotation.Autowired;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.*;

public class OAIProviderService {

    private final ObjectFactory objFactory;

    private final DatatypeFactory dataFactory;

    private final Unmarshaller unmarshaller;

    private final IdentifierTranslator subjectTranslator = new DefaultIdentifierTranslator();

    private final Model rdfModel = ModelFactory.createDefaultModel();

    private String identifyUri;

    private Map<String, MetadataFormat> metadataFormats;

    private DateTimeFormatter dateFormat = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC);

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
        final Datastream ds = this.datastreamService.getDatastream(session, createSubject(identifyUri));
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

    private String createSubject(String uri) throws RepositoryException {
        return subjectTranslator.getPathFromSubject(rdfModel.createResource(uri));
    }

    public JAXBElement<OAIPMHtype> listMetadataFormats(final Session session, final UriInfo uriInfo,
            final String identifier) throws RepositoryException {
        final String path = createSubject(uriInfo);
        final ListMetadataFormatsType listMetadataFormats = objFactory.createListMetadataFormatsType();
        if (identifier != null && !identifier.isEmpty()) {
            /* generate metadata format response for a single pid */
            if (!this.objectService.exists(session, "/" + identifier)) {
                return error(VerbType.LIST_METADATA_FORMATS, identifier, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                        "The object does not exist");
            }
            final FedoraObject obj = this.objectService.getObject(session, "/" + identifier);
            final Model model = obj.getPropertiesDataset(subjectTranslator).getDefaultModel();
            for (MetadataFormat mdf : metadataFormats.values()) {
                if (model.listObjectsOfProperty(rdfModel.createProperty(mdf.getPropertyName())).hasNext()) {
                    listMetadataFormats.getMetadataFormat().add(mdf.asMetadataFormatType());
                }
            }
        } else {
            /* generate a general metadataformat respose */
            listMetadataFormats.getMetadataFormat().addAll(listAvailableMetadataFormats());
        }

        final RequestType req = objFactory.createRequestType();
        req.setVerb(VerbType.LIST_METADATA_FORMATS);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final OAIPMHtype oai = objFactory.createOAIPMHtype();
        oai.setListMetadataFormats(listMetadataFormats);
        oai.setRequest(req);
        return objFactory.createOAIPMH(oai);
    }

    private List<MetadataFormatType> listObjectMetadataFormats(final Session session, final FedoraObject obj) throws RepositoryException {
        final List<MetadataFormatType> types = new ArrayList<>();
        return types;
    }

    private List<MetadataFormatType> listAvailableMetadataFormats() {
        final List<MetadataFormatType> types = new ArrayList<>(metadataFormats.size());
        for (MetadataFormat mdf : metadataFormats.values()) {
            final MetadataFormatType mdft = objFactory.createMetadataFormatType();
            mdft.setMetadataPrefix(mdf.getPrefix());
            mdft.setMetadataNamespace(mdf.getNamespace());
            mdft.setSchema(mdf.getSchemaUrl());
            types.add(mdft);
        }
        return types;
    }

    public void setMetadataFormats(final Map<String, MetadataFormat> metadataFormats) {
        this.metadataFormats = metadataFormats;
    }

    public String createSubject(final UriInfo uriInfo) throws RepositoryException {
        return subjectTranslator.getPathFromSubject(rdfModel.createResource(uriInfo.getRequestUri().toASCIIString()));
    }

    public JAXBElement<OAIPMHtype> getRecord(final Session session, final UriInfo uriInfo, final String identifier,
            final String metadataPrefix) throws RepositoryException {
        final MetadataFormat format = metadataFormats.get(metadataPrefix);
        if (format == null) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix,
                    OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT, "The metadata format is not available");
        }

        final String path = "/" + identifier;
        if (path == null || !this.objectService.exists(session, path)) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                    "The requested identifier does not exist");
        }
        final FedoraObject obj = this.objectService.getObject(session, path);
        final Model model = obj.getPropertiesDataset(subjectTranslator).getDefaultModel();
        final StmtIterator it = model.listStatements(subjectTranslator.getSubject("/" + identifier),
                model.createProperty("http://fedora.info/definitions/v4/config#", "hasOaiDCRecord"),
                (RDFNode) null);
        if (!it.hasNext()) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                    "The record does not have a oai meta data object associated");
        }

        final String dsPath = subjectTranslator.getPathFromSubject(it.next().getObject().asResource());
        if (!this.datastreamService.exists(session, dsPath)) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "The referenced datastream for the meta data can not be found");
        }
        final Datastream mdDs =
                this.datastreamService.getDatastream(session, dsPath);

        final OAIPMHtype oai = this.objFactory.createOAIPMHtype();
        final RequestType req = objFactory.createRequestType();
        req.setVerb(VerbType.GET_RECORD);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final GetRecordType getRecord = this.objFactory.createGetRecordType();
        final RecordType record = this.objFactory.createRecordType();
        getRecord.setRecord(record);

        final HeaderType header = this.objFactory.createHeaderType();
        header.setIdentifier(identifier);
        header.setDatestamp(dateFormat.print(new Date().getTime()));
        record.setHeader(header);
        // TODO: add set specs

        final MetadataType md = this.objFactory.createMetadataType();
        try {
            String content = IOUtils.toString(mdDs.getContent());
            md.setAny(new JAXBElement<String>(new QName(format.getPrefix()), String.class, content));
        } catch (IOException e) {
            throw new RepositoryException(e);
        } finally {
            IOUtils.closeQuietly(mdDs.getContent());
        }
        record.setMetadata(md);

        oai.setGetRecord(getRecord);
        return this.objFactory.createOAIPMH(oai);
    }

    public JAXBElement<OAIPMHtype> error(VerbType verb, String identifier, String metadataPrefix,
            OAIPMHerrorcodeType errorCode, String msg) {
        final OAIPMHtype oai = this.objFactory.createOAIPMHtype();
        final RequestType req = this.objFactory.createRequestType();
        req.setVerb(verb);
        req.setIdentifier(identifier);
        req.setMetadataPrefix(metadataPrefix);
        oai.setRequest(req);

        final OAIPMHerrorType error = this.objFactory.createOAIPMHerrorType();
        error.setCode(errorCode);
        error.setValue(msg);
        oai.getError().add(error);
        return this.objFactory.createOAIPMH(oai);
    }

    private void checkRequestedMetadataPrefix(final String prefix) throws RepositoryException {
        if (!metadataFormats.containsKey(prefix)) {
            throw new RepositoryException("Metadata prefix '" + prefix + "' is not available");
        }
    }

    public JAXBElement<OAIPMHtype> listIdentifiers(Session session, UriInfo uriInfo, String metadataPrefix,
            String from, String until, String set) throws RepositoryException {
        final MetadataFormat mdf = metadataFormats.get(metadataPrefix);
        if (mdf == null) {
            return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix,
                    OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT, "Unavailable metadata format");
        }
        final DateTime fromDateTime = (from != null && !from.isEmpty()) ? dateFormat.parseDateTime(from) : null;
        final DateTime untilDateTime = (until != null && !until.isEmpty()) ? dateFormat.parseDateTime(until) : null;
        final StringBuilder sparql =
                new StringBuilder("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> ")
                        .append("SELECT ?sub ?obj WHERE { ?sub <").append(mdf.getPropertyName()).append("> ?obj . ");
        if (fromDateTime != null) {
            sparql.append("?sub <").append(RdfLexicon.LAST_MODIFIED_DATE).append("> ?date . ")
                    .append("FILTER ( ?date >= \"2014-06-05T10:10:10+05:30\"^^xsd:dateTime )");
        }
        sparql.append(" }");
        try {
            final JQLConverter jql = new JQLConverter(session, subjectTranslator, sparql.toString());
            final ResultSet result = jql.execute();
            final OAIPMHtype oai = this.objFactory.createOAIPMHtype();
            final ListIdentifiersType ids = this.objFactory.createListIdentifiersType();
            if (!result.hasNext()) {
                return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "No record found");
            }
            while (result.hasNext()) {
                final HeaderType h = this.objFactory.createHeaderType();
                final QuerySolution sol = result.next();
                final Resource sub = sol.get("sub").asResource();
                h.setIdentifier(sub.getURI());
                final FedoraObject obj =
                        this.objectService.getObject(session, subjectTranslator.getPathFromSubject(sub));
                h.setDatestamp(dateFormat.print(obj.getLastModifiedDate().getTime()));
                // TODO: add sets
                ids.getHeader().add(h);
            }

            final RequestType req = this.objFactory.createRequestType();
            req.setVerb(VerbType.LIST_IDENTIFIERS);
            req.setMetadataPrefix(metadataPrefix);
            oai.setRequest(req);
            oai.setListIdentifiers(ids);
            return this.objFactory.createOAIPMH(oai);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }
}
