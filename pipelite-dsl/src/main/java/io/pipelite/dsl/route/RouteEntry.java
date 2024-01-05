/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
