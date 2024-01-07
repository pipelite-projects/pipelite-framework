package io.pipelite.dsl.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RecipientListImpl extends ArrayList<String> implements RecipientList {

    private final Map<String, Condition> conditions;

    public RecipientListImpl(){
        conditions = new HashMap<>();
    }

    public void addRecipient(String recipient){
        if(!contains(recipient)){
            add(recipient);
        }
    }

    public void addRecipientCondition(String recipient, Condition condition){
        addRecipient(recipient);
        conditions.put(recipient, condition);
    }

    @Override
    public boolean isConditional(String recipient){
        return conditions.containsKey(recipient);
    }

    @Override
    public Condition getCondition(String recipient) {
        return conditions.get(recipient);
    }

    @Override
    public <T extends Condition> T getConditionAs(String recipient, Class<T> expectedConditionType) {
        final Condition condition = getCondition(recipient);
        if(condition != null){
            if(expectedConditionType.isAssignableFrom(condition.getClass())){
                return expectedConditionType.cast(condition);
            }else {
                throw new ClassCastException(String.format("Unable to cast from %s to %s", condition.getClass(), expectedConditionType));
            }
        }
        return null;
    }

    @Override
    public <T extends Condition> Optional<T> tryGetConditionAs(String recipient, Class<T> expectedConditionType){
        return Optional.ofNullable(getConditionAs(recipient, expectedConditionType));
    }

}
