package io.pipelite.dsl.route;

import io.pipelite.common.support.Preconditions;

import java.util.Optional;

public class RouteEntry<V extends Condition> {

    private final RecipientList destination;
    private final V condition;

    public RouteEntry(RecipientList destination, V condition) {
        this.destination = Preconditions.notNull(destination, "destination is required and cannot be null");
        this.condition = Preconditions.notNull(condition, "condition is required and cannot be null");
    }

    public RecipientList getDestination(){
        return destination;
    }

    public Condition getCondition() {
        return condition;
    }

    public <T extends Condition> T getConditionAs(Class<T> expectedConditionType) {
        if(condition != null){
            if(expectedConditionType.isAssignableFrom(condition.getClass())){
                return expectedConditionType.cast(condition);
            }else {
                throw new ClassCastException(String.format("Unable to cast from %s to %s", condition.getClass(), expectedConditionType));
            }
        }
        return null;
    }

    public <T extends Condition> Optional<T> tryGetConditionAs(Class<T> expectedConditionType){
        return Optional.ofNullable(getConditionAs(expectedConditionType));
    }
}
