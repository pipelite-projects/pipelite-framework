package io.pipelite.spi.flow;

public interface PostProcessor {

    int LOWEST_PRECEDENCE = Integer.MIN_VALUE;
    int HIGHEST_PRECEDENCE = Integer.MAX_VALUE;

    default int getOrder(){
        return 0;
    }

}
