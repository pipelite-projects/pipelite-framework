package io.pipelite.core.support.serialization;

public interface ObjectSerializer {

    String getEncoding();
    String serializeObject(Object input);

}
