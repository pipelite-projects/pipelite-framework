package io.pipelite.core.support.serialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class ObjectToByteArrayConverter {

    public byte[] convert(Object object){

        ObjectOutputStream oos = null;
        try {

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);

            return baos.toByteArray();

        }catch(IOException exception){
            throw new ByteArrayWriteException("An underlying error occurred writing object", exception);
        } finally {
            if(oos != null){
                try {
                    oos.close();
                }catch (IOException ignored){}
            }
        }
    }
}
