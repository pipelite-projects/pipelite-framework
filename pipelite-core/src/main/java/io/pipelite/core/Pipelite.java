package io.pipelite.core;

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.definition.builder.FlowDefinitionBuilder;

public class Pipelite {

    private Pipelite(){}

    public static FlowDefinitionBuilder defineFlow(String flowName){
        return new FlowDefinitionBuilder(flowName);
    }

    public static PipeliteContext createContext(){
        return new DefaultPipeliteContext();
    }

}
