package io.pipelite.examples.banking.app.core.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHelper {

    private static final String BANKING_LOGGER_NAME = "banking";

    private LogHelper(){}

    public static Logger getBankingLogger(){
        return LoggerFactory.getLogger(BANKING_LOGGER_NAME);
    }
}
