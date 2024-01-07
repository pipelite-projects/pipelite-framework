package io.pipelite.components.http.support.convert;

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.util.MimeType;

public abstract class AbstractHttpMessageBodyConverter implements HttpMessageBodyConverter {

    private final MimeType contentType;

    public AbstractHttpMessageBodyConverter(MimeType contentType) {
        this.contentType = Preconditions.notNull(contentType, "contentType is required and cannot be null");
    }

    @Override
    public boolean supports(MimeType contentType) {
        return this.contentType.isCompatibleWith(contentType);
    }
}
