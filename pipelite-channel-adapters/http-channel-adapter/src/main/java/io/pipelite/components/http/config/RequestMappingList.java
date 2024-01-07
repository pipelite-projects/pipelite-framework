package io.pipelite.components.http.config;

import io.pipelite.common.util.MimeType;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class RequestMappingList extends CopyOnWriteArrayList<RequestMapping> {

    public Optional<RequestMapping> tryFindBestMatch(String requestPath, RequestMethod requestMethod, MimeType contentType){
        return stream()
            .filter(requestMapping -> {
                boolean isCandidate = requestMapping.getRequestPath().equalsIgnoreCase(requestPath);
                if (requestMapping.hasRequestMethod()) {
                    isCandidate &= requestMapping.getRequestMethod() == requestMethod;
                }
                if (requestMapping.hasContentType()) {
                    isCandidate &= requestMapping.isContentTypeCompatible(contentType);
                }
                return isCandidate;
            }).max(Comparator.comparing(RequestMapping::getSpecificityLevel));
    }
}
