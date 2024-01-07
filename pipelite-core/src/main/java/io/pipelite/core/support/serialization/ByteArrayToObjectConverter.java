package io.pipelite.core.support.serialization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ByteArrayToObjectConverter {

    public <T> T convert(byte[] content, Class<T> expectedType){

        ObjectInputStream ois = null;
        try{
            final ByteArrayInputStream bais = new ByteArrayInputStream(content);
            ois = new ObjectInputStream(bais);
            final Object object = ois.readObject();
            if(object == null){
                return null;
            } else {
                if(expectedType.isAssignableFrom(object.getClass())){
                    return expectedType.cast(object);
                }
                throw new ClassCastException(String.format("Unable to cast from %s to %s", object.getClass(), expectedType));
            }
        } catch (IOException | ClassNotFoundException exception){
            throw new ByteArrayReadException("An underlying error occurred reading serialized content", exception);
        } finally {
            try {
                if(ois != null){
                    ois.close();
                }
            }catch (IOException ignore){}
        }

    }
}
