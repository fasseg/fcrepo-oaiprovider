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

import org.openarchives.oai._2.MetadataFormatType;
import org.openarchives.oai._2.ObjectFactory;

public class MetadataFormat {

    private String prefix;

    private String schemaUrl;

    private String namespace;

    private String propertyName;

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setSchemaUrl(String schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSchemaUrl() {
        return schemaUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public MetadataFormatType asMetadataFormatType() {
        final ObjectFactory objectFactory = new ObjectFactory();
        final MetadataFormatType type = objectFactory.createMetadataFormatType();
        type.setSchema(schemaUrl);
        type.setMetadataNamespace(namespace);
        type.setMetadataPrefix(prefix);
        return type;

    }
}
