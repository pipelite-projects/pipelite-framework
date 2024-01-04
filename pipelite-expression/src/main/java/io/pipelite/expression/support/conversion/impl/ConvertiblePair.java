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
package io.pipelite.expression.support.conversion.impl;

/**
 * @author
 *
 */
public class ConvertiblePair {

    private final Class<?> sourceType;

    private final Class<?> targetType;

    public ConvertiblePair(Class<?> sourceType, Class<?> targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sourceType == null) ? 0 : sourceType.hashCode());
        result = prime * result + ((targetType == null) ? 0 : targetType.hashCode());
        return result;
    }

    /**
     * @return
     */
    public Class<?> getSourceType() {
        return sourceType;
    }

    /**
     * @return
     */
    public Class<?> getTargetType() {
        return targetType;
    }

    /**
     * @param sourceType
     * @param targetType
     * @return
     */
    public boolean isAssignableFrom(Class<?> sourceType, Class<?> targetType) {
        return this.sourceType.isAssignableFrom(sourceType) && this.targetType.isAssignableFrom(targetType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConvertiblePair other = (ConvertiblePair) obj;
        if (sourceType == null) {
            if (other.sourceType != null)
                return false;
        }
        else if (!sourceType.equals(other.sourceType))
            return false;
        if (targetType == null) {
            if (other.targetType != null)
                return false;
        }
        else if (!targetType.equals(other.targetType))
            return false;
        return true;
    }

}
