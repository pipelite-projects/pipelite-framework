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
package io.pipelite.spi.channel;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelURL {

    private static final String URL_PATTERN_REGEX = "^((.+)://)*([a-zA-Z0-9-_.?=&]+)";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_PATTERN_REGEX);

    private final String protocol;
    private final String endpointURL;

    private ChannelURL(String protocol, String endpointURL){
        this.protocol = protocol;
        this.endpointURL = endpointURL;
    }

    public boolean hasProtocol(){
        return protocol != null;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getEndpointURL() {
        return endpointURL;
    }

    public static ChannelURL parse(String url) {
        final Matcher matcher = URL_PATTERN.matcher(url);
        if(!matcher.matches()){
            throw new IllegalArgumentException(String.format("Malformed endpoint url %s", url));
        }
        final String protocol = matcher.group(2);
        final String endpointURL = matcher.group(3);
        return new ChannelURL(protocol, endpointURL);
    }

}