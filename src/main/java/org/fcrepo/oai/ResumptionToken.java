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

public class ResumptionToken {

    private final String verb;

    private final String from;

    private final String until;

    private final int offset;

    private final String set;

    private final String metadataPrefix;

    public ResumptionToken(String verb, String metadataPrefix, String from, String until, int offset, String set) {
        this.verb = verb;
        this.from = from;
        this.metadataPrefix = metadataPrefix;
        this.until = until;
        this.offset = offset;
        this.set = set;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public int getOffset() {
        return offset;
    }

    public String getVerb() {
        return verb;
    }

    public String getFrom() {
        return from;
    }

    public String getUntil() {
        return until;
    }

    public String getSet() {
        return set;
    }
}
