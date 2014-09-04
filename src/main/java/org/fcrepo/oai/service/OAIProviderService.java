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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import javax.annotation.PostConstruct;
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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.fcrepo.http.api.FedoraNodes;
import org.fcrepo.http.commons.api.rdf.HttpIdentifierTranslator;
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
import org.fcrepo.oai.ResumptionToken;
import org.fcrepo.oai.http.OAIWebResource;
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

    private static final ObjectFactory oaiFactory = new ObjectFactory();

    private final DatatypeFactory dataFactory;

    private final Unmarshaller unmarshaller;

    private final Model rdfModel = ModelFactory.createDefaultModel();

    private String identifyPath;

    private String setsRootPath;

    private String propertyHasSets;

    private String propertySetName;

    private String propertyHasSetSpec;

    private String propertyIsPartOfSet;

    private boolean setsEnabled;

    private Map<String, MetadataFormat> metadataFormats;

    private DateTimeFormatter dateFormat = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC);

    private int maxListSize;

    @Autowired
    private DatastreamService datastreamService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private ObjectService objectService;

    public void setPropertyHasSetSpec(String propertyHasSetSpec) {
        this.propertyHasSetSpec = propertyHasSetSpec;
    }

    public void setPropertySetName(String propertySetName) {
        this.propertySetName = propertySetName;
    }

    public void setPropertyHasSets(String propertyHasSets) {
        this.propertyHasSets = propertyHasSets;
    }

    public void setMaxListSize(int maxListSize) {
        this.maxListSize = maxListSize;
    }

    public void setPropertyIsPartOfSet(String propertyIsPartOfSet) {
        this.propertyIsPartOfSet = propertyIsPartOfSet;
    }

    public void setSetsRootPath(String setsRootPath) {
        this.setsRootPath = setsRootPath;
    }

    public void setSetsEnabled(boolean setsEnabled) {
        this.setsEnabled = setsEnabled;
    }

    public void setIdentifyPath(String identifyPath) {
        this.identifyPath = identifyPath;
    }

    @PostConstruct
    public void init() throws RepositoryException {
        /* check if set root node exists */
        Session session = sessionFactory.getInternalSession();
        if (!this.objectService.exists(session, setsRootPath)) {
            this.objectService.createObject(session, setsRootPath);
        }
        session.save();
    }

    public OAIProviderService() throws DatatypeConfigurationException, JAXBException {
        this.dataFactory = DatatypeFactory.newInstance();
        this.unmarshaller =
                JAXBContext.newInstance(OAIPMHtype.class, IdentifyType.class, SetType.class).createUnmarshaller();
    }

    public JAXBElement<OAIPMHtype> identify(final Session session, UriInfo uriInfo) throws RepositoryException,
            JAXBException {
        final Datastream ds = this.datastreamService.getDatastream(session, identifyPath);
        final InputStream data = ds.getContent();
        final IdentifyType id = this.unmarshaller.unmarshal(new StreamSource(data), IdentifyType.class).getValue();

        final RequestType req = oaiFactory.createRequestType();
        req.setVerb(VerbType.IDENTIFY);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
        oai.setIdentify(id);
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(new GregorianCalendar()));
        oai.setRequest(req);
        return oaiFactory.createOAIPMH(oai);
    }

    public JAXBElement<OAIPMHtype> listMetadataFormats(final Session session, final UriInfo uriInfo,
            final String identifier) throws RepositoryException {
        final HttpIdentifierTranslator translator =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);
        final ListMetadataFormatsType listMetadataFormats = oaiFactory.createListMetadataFormatsType();
        if (identifier != null && !identifier.isEmpty()) {
            final Resource subject = translator.getSubject("/" + identifier);
            final String path = translator.getPathFromSubject(subject);
            if (path != null && !path.isEmpty()) {
                /* generate metadata format response for a single pid */
                if (!this.objectService.exists(session, path)) {
                    return error(VerbType.LIST_METADATA_FORMATS, identifier, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                            "The object does not exist");
                }
                final FedoraObject obj = this.objectService.getObject(session, "/" + identifier);
                final Model model = obj.getPropertiesDataset(translator).getDefaultModel();
                for (MetadataFormat mdf : metadataFormats.values()) {
                    if (model.listObjectsOfProperty(rdfModel.createProperty(mdf.getPropertyName())).hasNext()) {
                        listMetadataFormats.getMetadataFormat().add(mdf.asMetadataFormatType());
                    } else {
                        return error(VerbType.LIST_METADATA_FORMATS, identifier, null,
                                OAIPMHerrorcodeType.NO_METADATA_FORMATS, "No metadata available");
                    }
                }
            }
        } else {
            /* generate a general metadata format response */
            listMetadataFormats.getMetadataFormat().addAll(listAvailableMetadataFormats());
        }

        final RequestType req = oaiFactory.createRequestType();
        req.setVerb(VerbType.LIST_METADATA_FORMATS);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
        oai.setListMetadataFormats(listMetadataFormats);
        oai.setRequest(req);
        return oaiFactory.createOAIPMH(oai);
    }

    private List<MetadataFormatType> listObjectMetadataFormats(final Session session, final FedoraObject obj) throws RepositoryException {
        final List<MetadataFormatType> types = new ArrayList<>();
        return types;
    }

    private List<MetadataFormatType> listAvailableMetadataFormats() {
        final List<MetadataFormatType> types = new ArrayList<>(metadataFormats.size());
        for (MetadataFormat mdf : metadataFormats.values()) {
            final MetadataFormatType mdft = oaiFactory.createMetadataFormatType();
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

    public JAXBElement<OAIPMHtype> getRecord(final Session session, final UriInfo uriInfo, final String identifier,
            final String metadataPrefix) throws RepositoryException {
        final HttpIdentifierTranslator translator = new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);
        final MetadataFormat format = metadataFormats.get(metadataPrefix);
        if (format == null) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix,
                    OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT, "The metadata format is not available");
        }
        final Resource subject = translator.getSubject("/" + identifier);
        final String path = translator.getPathFromSubject(subject);
        if (!this.objectService.exists(session, path)) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                    "The requested identifier does not exist");
        }
        final FedoraObject obj = this.objectService.getObject(session, path);
        final Model model = obj.getPropertiesDataset(translator).getDefaultModel();
        final StmtIterator it = model.listStatements(subject,
                model.createProperty(format.getPropertyName()),
                (RDFNode) null);
        if (!it.hasNext()) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                    "The record does not have a oai meta data object associated");
        }

        final String dsPath = translator.getPathFromSubject(it.next().getObject().asResource());
        if (!this.datastreamService.exists(session, dsPath)) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "The referenced datastream for the meta data can not be found");
        }

        final Datastream mdDs =
                this.datastreamService.getDatastream(session, dsPath);
        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
        final RequestType req = oaiFactory.createRequestType();
        req.setVerb(VerbType.GET_RECORD);
        req.setValue(uriInfo.getRequestUri().toASCIIString());

        final GetRecordType getRecord = oaiFactory.createGetRecordType();
        final RecordType record = oaiFactory.createRecordType();
        getRecord.setRecord(record);

        final HeaderType header = oaiFactory.createHeaderType();
        header.setIdentifier(identifier);
        header.setDatestamp(dateFormat.print(new Date().getTime()));
        final StmtIterator itSets = model.listStatements(translator.getSubject("/" + identifier),
                model.createProperty(propertyIsPartOfSet),
                (RDFNode) null);
        if (itSets.hasNext()) {
            header.getSetSpec().add(itSets.next().getObject().asLiteral().getString());
        }
        record.setHeader(header);
        // TODO: add set specs

        final MetadataType md = oaiFactory.createMetadataType();
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
        return oaiFactory.createOAIPMH(oai);
    }

    public static JAXBElement<OAIPMHtype> error(VerbType verb, String identifier, String metadataPrefix,
            OAIPMHerrorcodeType errorCode, String msg) {
        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
        final RequestType req = oaiFactory.createRequestType();
        req.setVerb(verb);
        req.setIdentifier(identifier);
        req.setMetadataPrefix(metadataPrefix);
        oai.setRequest(req);

        final OAIPMHerrorType error = oaiFactory.createOAIPMHerrorType();
        error.setCode(errorCode);
        error.setValue(msg);
        oai.getError().add(error);
        return oaiFactory.createOAIPMH(oai);
    }

    private void checkRequestedMetadataPrefix(final String prefix) throws RepositoryException {
        if (!metadataFormats.containsKey(prefix)) {
            throw new RepositoryException("Metadata prefix '" + prefix + "' is not available");
        }
    }

    public JAXBElement<OAIPMHtype> listIdentifiers(Session session, UriInfo uriInfo, String metadataPrefix,
            String from, String until, String set, int offset) throws RepositoryException {

        final HttpIdentifierTranslator translator =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);
        if (metadataPrefix == null) {
            return error(VerbType.LIST_IDENTIFIERS, null, null, OAIPMHerrorcodeType.BAD_ARGUMENT, "metadataprefix is invalid");
        }
        final MetadataFormat mdf = metadataFormats.get(metadataPrefix);
        if (mdf == null) {
            return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix,
                    OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT, "Unavailable metadata format");
        }
        DateTime fromDateTime = null;
        DateTime untilDateTime = null;
        try {
            fromDateTime = (from != null && !from.isEmpty()) ? dateFormat.parseDateTime(from) : null;
            untilDateTime = (until != null && !until.isEmpty()) ? dateFormat.parseDateTime(until) : null;
        } catch (IllegalArgumentException e) {
            return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, e.getMessage());
        }
        
        final StringBuilder sparql =
                new StringBuilder("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> ")
                        .append("SELECT ?sub ?obj WHERE { ")
                        .append("?sub <").append(mdf.getPropertyName()).append("> ?obj . ");

        final List<String> filters = new ArrayList<>();

        if (fromDateTime != null || untilDateTime != null) {
            sparql.append("?sub <").append(RdfLexicon.LAST_MODIFIED_DATE).append("> ?date . ");
            if (fromDateTime != null) {
                filters.add("?date >='" + from + "'^^xsd:dateTime ");
            }
            if (untilDateTime!= null) {
                filters.add("?date <='" + until + "'^^xsd:dateTime ");
            }
        }

        if (set != null && !set.isEmpty()) {
            if (!setsEnabled) {
                return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.NO_SET_HIERARCHY, "Sets are not enabled");
            }
            sparql.append("?sub <").append(propertyIsPartOfSet).append("> ?set . ");
            filters.add("?set = '" + set + "'");
        }

        int filterCount = 0;
        for (String filter:filters) {
            if (filterCount++ == 0) {
                sparql.append("FILTER (");
            }
            sparql.append(filter).append(filterCount == filters.size() ? ")" : " && ");
        }
        sparql.append("}")
                .append(" OFFSET ").append(offset)
                .append(" LIMIT ").append(maxListSize);
        try {
            final JQLConverter jql = new JQLConverter(session, translator, sparql.toString());
            final ResultSet result = jql.execute();
            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            final ListIdentifiersType ids = oaiFactory.createListIdentifiersType();
            if (!result.hasNext()) {
                return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "No record found");
            }
            while (result.hasNext()) {
                final HeaderType h = oaiFactory.createHeaderType();
                final QuerySolution sol = result.next();
                final Resource sub = sol.get("sub").asResource();
                final Resource oaiRecordUri = sol.get("obj").asResource();
                if (!this.datastreamService.exists(session, translator.getPathFromSubject(oaiRecordUri))) {
                    continue;
                }
                h.setIdentifier(sub.getURI());
                final FedoraObject obj =
                        this.objectService.getObject(session, translator.getPathFromSubject(sub));
                h.setDatestamp(dateFormat.print(obj.getLastModifiedDate().getTime()));
                // get set names this object is part of
                final Model objModel = obj.getPropertiesDataset(translator).getDefaultModel();
                final StmtIterator setNames = objModel.listStatements(translator.getSubject(obj.getPath()), objModel.createProperty(propertyIsPartOfSet), (RDFNode) null);
                while (setNames.hasNext()) {
                    final FedoraObject setObject = this.objectService.getObject(session, setsRootPath + "/" + setNames.next().getObject().asLiteral().getString());
                    final Model setObjectModel = setObject.getPropertiesDataset(translator).getDefaultModel();
                    final StmtIterator setSpec = setObjectModel.listStatements(translator.getSubject(setObject.getPath()), objModel.createProperty(propertyHasSetSpec), (RDFNode) null);
                    if (setSpec.hasNext()) {
                        h.getSetSpec().add(setSpec.next().getObject().asLiteral().getString());
                    }
                }
                ids.getHeader().add(h);
            }

            final RequestType req = oaiFactory.createRequestType();
            if (ids.getHeader().size() == maxListSize) {
                req.setResumptionToken(encodeResumptionToken(VerbType.LIST_IDENTIFIERS.value(), metadataPrefix, from,
                        until, set,
                        offset + maxListSize));
            }
            req.setVerb(VerbType.LIST_IDENTIFIERS);
            req.setMetadataPrefix(metadataPrefix);
            oai.setRequest(req);
            oai.setListIdentifiers(ids);
            return oaiFactory.createOAIPMH(oai);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    public static String encodeResumptionToken(String verb, String metadataPrefix, String from, String until,
            String set, int offset) throws UnsupportedEncodingException {
        if (from == null) {
            from = "";
        }
        if (until == null) {
            until = "";
        }
        if (set == null) {
            set = "";
        }
        String[] data = new String[] {
            urlEncode(verb),
            urlEncode(metadataPrefix),
            urlEncode(from),
            urlEncode(until),
            urlEncode(set),
            urlEncode(String.valueOf(offset))
        };
        return Base64.encodeBase64URLSafeString(StringUtils.join(data, ':').getBytes("UTF-8"));
    }

    public static String urlEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }

    public static String urlDecode(String value) throws UnsupportedEncodingException {
        return URLDecoder.decode(value, "UTF-8");
    }

    public static ResumptionToken decodeResumptionToken(String token) throws UnsupportedEncodingException {
        String[] data = StringUtils.splitPreserveAllTokens(new String(Base64.decodeBase64(token)), ':');
        final String verb = urlDecode(data[0]);
        final String metadataPrefix = urlDecode(data[1]);
        final String from = urlDecode(data[2]);
        final String until = urlDecode(data[3]);
        final String set = urlDecode(data[4]);
        final int offset = Integer.parseInt(urlDecode(data[5]));
        return new ResumptionToken(verb, metadataPrefix, from, until, offset, set);
    }

    public JAXBElement<OAIPMHtype> listSets(Session session, UriInfo uriInfo, int offset) throws RepositoryException {
        final HttpIdentifierTranslator translator =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);
        try {
            if (!setsEnabled) {
                return error(VerbType.LIST_SETS, null, null, OAIPMHerrorcodeType.NO_SET_HIERARCHY, "Set are not enabled");
            }
            final StringBuilder sparql = new StringBuilder("SELECT ?obj WHERE {")
                    .append("<").append(translator.getSubject(setsRootPath)).append(">")
                    .append("<").append(propertyHasSets).append("> ?obj }");
            final JQLConverter jql = new JQLConverter(session, translator, sparql.toString());
            final ResultSet result = jql.execute();
            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            final ListSetsType sets = oaiFactory.createListSetsType();
            while (result.hasNext()) {
                final Resource setRes = result.next().get("obj").asResource();
                sparql.setLength(0);
                sparql.append("SELECT ?name ?spec WHERE {")
                        .append("<").append(setRes).append("> ")
                        .append("<").append(propertySetName).append("> ")
                        .append("?name ; ")
                        .append("<").append(propertyHasSetSpec).append("> ")
                        .append("?spec . ")
                        .append("}");
                final JQLConverter setJql = new JQLConverter(session, translator, sparql.toString());
                final ResultSet setResult = setJql.execute();
                while (setResult.hasNext()) {
                    final SetType set = oaiFactory.createSetType();
                    QuerySolution sol = setResult.next();
                    set.setSetName(sol.get("name").asLiteral().getString());
                    set.setSetSpec(sol.get("spec").asLiteral().getString());
                    sets.getSet().add(set);
                }
            }
            oai.setListSets(sets);
            return oaiFactory.createOAIPMH(oai);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    public String createSet(Session session, UriInfo uriInfo, InputStream src) throws RepositoryException {
        final HttpIdentifierTranslator translator =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);
        try {
            final SetType set = this.unmarshaller.unmarshal(new StreamSource(src), SetType.class).getValue();
            final String setId = getSetId(set);
            final FedoraObject setRoot = this.objectService.getObject(session, setsRootPath);
            if (set.getSetSpec() != null) {
                /* validate that the hierarchy of sets exists */
            }

            final FedoraObject setObject = this.objectService.createObject(session, setsRootPath + "/" + setId);

            StringBuilder sparql =
                    new StringBuilder("INSERT DATA {<" + translator.getSubject(setRoot.getPath()) + "> <" +
                            propertyHasSets + "> <" + translator.getSubject(setObject.getPath()) + ">}");
            setRoot.updatePropertiesDataset(translator, sparql.toString());

            sparql.setLength(0);
            sparql.append("INSERT DATA {")
                    .append("<" + translator.getSubject(setObject.getPath()) + "> <" + propertySetName +
                            "> '" + set.getSetName() + "' .")
                    .append("<" + translator.getSubject(setObject.getPath()) + "> <" + propertyHasSetSpec +
                            "> '" + set.getSetName() + "' .");
            for (DescriptionType desc : set.getSetDescription()) {
                // TODO: save description
            }
            sparql.append("}");
            setObject.updatePropertiesDataset(translator, sparql.toString());
            session.save();
            return setObject.getPath();
        } catch (JAXBException e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    private String getSetId(SetType set) throws RepositoryException {
        if (set.getSetSpec() == null) {
            throw new RepositoryException("SetSpec can not be empty");
        }
        String id = set.getSetSpec();
        int colonPos = id.indexOf(':');
        while (colonPos > 0) {
            id = id.substring(colonPos + 1);
        }
        return id;
    }

    public JAXBElement<OAIPMHtype> listRecords(Session session, UriInfo uriInfo, String metadataPrefix, String from, String until, String set, int offset) throws RepositoryException {

        final HttpIdentifierTranslator translator =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);

        if (metadataPrefix == null) {
            return error(VerbType.LIST_RECORDS, null, null, OAIPMHerrorcodeType.BAD_ARGUMENT, "metadataprefix is invalid");
        }
        final MetadataFormat mdf = metadataFormats.get(metadataPrefix);
        if (mdf == null) {
            return error(VerbType.LIST_RECORDS, null, metadataPrefix,
                    OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT, "Unavailable metadata format");
        }
        DateTime fromDateTime = null;
        DateTime untilDateTime = null;
        try {
            fromDateTime = (from != null && !from.isEmpty()) ? dateFormat.parseDateTime(from) : null;
            untilDateTime = (until != null && !until.isEmpty()) ? dateFormat.parseDateTime(until) : null;
        } catch (IllegalArgumentException e) {
            return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, e.getMessage());
        }

        final StringBuilder sparql =
                new StringBuilder("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> ")
                        .append("SELECT ?sub ?obj WHERE { ")
                        .append("?sub <").append(mdf.getPropertyName()).append("> ?obj . ");

        final List<String> filters = new ArrayList<>();

        if (fromDateTime != null || untilDateTime != null) {
            sparql.append("?sub <").append(RdfLexicon.LAST_MODIFIED_DATE).append("> ?date . ");
            if (fromDateTime != null) {
                filters.add("?date >='" + from + "'^^xsd:dateTime ");
            }
            if (untilDateTime!= null) {
                filters.add("?date <='" + until + "'^^xsd:dateTime ");
            }
        }

        if (set != null && !set.isEmpty()) {
            if (!setsEnabled) {
                return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.NO_SET_HIERARCHY, "Sets are not enabled");
            }
            sparql.append("?sub <").append(propertyIsPartOfSet).append("> ?set . ");
            filters.add("?set = '" + set + "'");
        }

        int filterCount = 0;
        for (String filter:filters) {
            if (filterCount++ == 0) {
                sparql.append("FILTER (");
            }
            sparql.append(filter).append(filterCount == filters.size() ? ")" : " && ");
        }
        sparql.append("}")
                .append(" OFFSET ").append(offset)
                .append(" LIMIT ").append(maxListSize);
        try {
            final JQLConverter jql = new JQLConverter(session, translator, sparql.toString());
            final ResultSet result = jql.execute();
            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            final ListRecordsType records = oaiFactory.createListRecordsType();
            if (!result.hasNext()) {
                return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "No record found");
            }
            while (result.hasNext()) {
                // check if the records exists
                final RecordType record = oaiFactory.createRecordType();
                final HeaderType h = oaiFactory.createHeaderType();
                final QuerySolution solution = result.next();
                final Resource subjectUri = solution.get("sub").asResource();
                final Resource oaiRecordUri = solution.get("obj").asResource();
                if (!this.datastreamService.exists(session, translator.getPathFromSubject(oaiRecordUri))) {
                    continue;
                }
                h.setIdentifier(subjectUri.getURI());
                final FedoraObject obj =
                        this.objectService.getObject(session, translator.getPathFromSubject(subjectUri));
                h.setDatestamp(dateFormat.print(obj.getLastModifiedDate().getTime()));
                // get set names this object is part of
                final Model objModel = obj.getPropertiesDataset(translator).getDefaultModel();
                final StmtIterator setNames = objModel.listStatements(translator.getSubject(obj.getPath()), objModel.createProperty(propertyIsPartOfSet), (RDFNode) null);
                while (setNames.hasNext()) {
                    final FedoraObject setObject = this.objectService.getObject(session, setsRootPath + "/" + setNames.next().getObject().asLiteral().getString());
                    final Model setObjectModel = setObject.getPropertiesDataset(translator).getDefaultModel();
                    final StmtIterator setSpec = setObjectModel.listStatements(translator.getSubject(setObject.getPath()), objModel.createProperty(propertyHasSetSpec), (RDFNode) null);
                    if (setSpec.hasNext()) {
                        h.getSetSpec().add(setSpec.next().getObject().asLiteral().getString());
                    }
                }
                // get the metadata record from fcrepo
                final MetadataType md = oaiFactory.createMetadataType();
                final Datastream mdDs = this.datastreamService.getDatastream(session, translator.getPathFromSubject(oaiRecordUri));
                try {
                    String content = IOUtils.toString(mdDs.getContent());
                    md.setAny(new JAXBElement<String>(new QName(mdf.getPrefix()), String.class, content));
                } catch (IOException e) {
                    throw new RepositoryException(e);
                } finally {
                    IOUtils.closeQuietly(mdDs.getContent());
                }
                record.setMetadata(md);
                record.setHeader(h);
                records.getRecord().add(record);
            }

            final RequestType req = oaiFactory.createRequestType();
            if (records.getRecord().size() == maxListSize) {
                req.setResumptionToken(encodeResumptionToken(VerbType.LIST_RECORDS.value(), metadataPrefix, from,
                        until, set,
                        offset + maxListSize));
            }
            req.setVerb(VerbType.LIST_RECORDS);
            req.setMetadataPrefix(metadataPrefix);
            oai.setRequest(req);
            oai.setListRecords(records);
            return oaiFactory.createOAIPMH(oai);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }
}
