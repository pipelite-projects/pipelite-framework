/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pipelite.spi.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointURL {

    // ^(([a-z0-9]+):\/\/)?([-a-z0-9]+)(\?([-a-zA-Z0-9_&=]{1,256})){0,1}$

    //private static final String URL_PATTERN_REGEX = "^(([a-z0-9]+):/{2})?([-a-z0-9]+)(\\?([-a-zA-Z0-9_&=]{1,256}))?$";

    private static final String URL_PATTERN_REGEX = "^([a-z0-9-_.]+)(\\?([a-zA-Z0-9-._&=]{1,256}))?$";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_PATTERN_REGEX);
    private final String resource;
    private final Map<String, String> parameters;

    private EndpointURL(String resource, Map<String, String> parameters){
        this.resource = resource;
        this.parameters = parameters != null ? parameters : new LinkedHashMap<>();
    }

    public String getResource(){
        return resource;
    }

    public EndpointProperties getProperties(){
        return new EndpointProperties(parameters);
    }

    public boolean sameURIAs(String uri){
        return resource.equals(uri);
    }

    public static EndpointURL parse(String url) {

        final Map<String, String> queryPairs = new LinkedHashMap<>();
        final Matcher matcher = URL_PATTERN.matcher(url);
        if(!matcher.matches()){
            throw new IllegalArgumentException(String.format("Malformed endpoint url '%s'. Ensure to use only [-a-z0-9] for resource characters!", url));
        }

        final String resource = matcher.group(1);
        final String query = matcher.group(3);
        if(query != null){
            final String[] pairs = query.split("&");
            for (String pair : pairs) {
                final String[] keyValue = pair.split("=");
                if(keyValue.length == 2){
                    final String key = keyValue[0];
                    final String value = keyValue[1];
                    if (!queryPairs.containsKey(key)) {
                        queryPairs.put(key, value);
                    }
                }
            }
        }
        return new EndpointURL(resource, queryPairs);
    }
}
