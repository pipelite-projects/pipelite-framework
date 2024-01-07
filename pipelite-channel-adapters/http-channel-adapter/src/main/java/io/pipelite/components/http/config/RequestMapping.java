package io.pipelite.components.http.config;

import io.pipelite.common.support.Builder;
import io.pipelite.common.support.Preconditions;
import io.pipelite.common.util.MimeType;
import io.pipelite.common.util.MimeTypeUtils;

public class RequestMapping {

    private final String requestPath;

    private RequestMethod requestMethod;

    private MimeType contentType;

    private Class<?> payloadType;

    public static RequestMappingBuilder define(String requestPath){
        return new RequestMappingBuilder(requestPath);
    }

    public RequestMapping(String requestPath) {
        this.requestPath = Preconditions.hasText(requestPath, "Illegal requestPath provided");
    }

    public String getRequestPath() {
        return requestPath;
    }

    public boolean hasRequestMethod() {
        return requestMethod != null;
    }

    public RequestMethod getRequestMethod() {
        return requestMethod;
    }

    public boolean hasContentType() {
        return contentType != null;
    }

    public Class<?> getPayloadType(){
        return payloadType;
    }

    public boolean isContentTypeCompatible(MimeType contentType){
        return this.contentType != null
            && this.contentType.isCompatibleWith(contentType);
    }

    public int getSpecificityLevel(){
        int level = 1;
        if (requestMethod != null) {
            ++level;
        }
        if (contentType != null && !contentType.isWildcardType()) {
            ++level;
            if (!contentType.isWildcardSubtype()) {
                ++level;
            }
        }
        return level;
    }

    public static final class RequestMappingBuilder {

        private final Builder<RequestMapping> builder;

        private RequestMappingBuilder(String requestPath) {

            Preconditions.hasText(requestPath, "Invalid requestPath provided");

            builder = Builder.forType(RequestMapping.class);

            requestPath = requestPath.startsWith("/") ? requestPath.replaceFirst("/", "") : requestPath;
            builder.constructWith(requestPath);

        }

        public RequestMappingBuilder withPostMethod() {
            withRequestMethod(RequestMethod.POST);
            return this;
        }

        public RequestMappingBuilder withPutMethod() {
            withRequestMethod(RequestMethod.PUT);
            return this;
        }

        public RequestMappingBuilder withRequestMethod(RequestMethod requestMethod) {
            builder.with(i -> i.requestMethod = requestMethod);
            return this;
        }

        public RequestMappingBuilder withContentType(String contentType) {
            builder.with(i -> i.contentType = MimeTypeUtils.parseMimeType(contentType));
            return this;
        }

        public RequestMappingBuilder withPayloadType(Class<?> payloadType){
            builder.with(i -> i.payloadType = payloadType);
            return this;
        }

        public RequestMapping build(){
            return builder.build();
        }
    }
}
