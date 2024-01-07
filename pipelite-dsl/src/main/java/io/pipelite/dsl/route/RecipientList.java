package io.pipelite.dsl.route;

import java.util.Collection;
import java.util.Optional;

public interface RecipientList extends Collection<String> {

    static RecipientList of(String... recipients){
        final RecipientListImpl recipientList = new RecipientListImpl();
        for(String recipient : recipients){
            recipientList.addRecipient(recipient);
        }
        return recipientList;
    }

    boolean isConditional(String recipient);

    Condition getCondition(String recipient);

    <T extends Condition> T getConditionAs(String recipient, Class<T> expectedConditionType);

    <T extends Condition> Optional<T> tryGetConditionAs(String recipient, Class<T> expectedConditionType);
}
