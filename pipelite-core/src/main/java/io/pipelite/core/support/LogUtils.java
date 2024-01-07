package io.pipelite.core.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils {

    private LogUtils(){
    }

    public static Logger getRootLogger(){
        return LoggerFactory.getLogger("io.pipelite");
    }

    public static String formatTag(String flowName, String resource){
        return String.format("[%s/%s]", flowName, resource);
    }
}
