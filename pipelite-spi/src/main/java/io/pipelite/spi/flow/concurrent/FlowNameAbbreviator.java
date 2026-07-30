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
package io.pipelite.spi.flow.concurrent;

/**
 * Shortens a kebab-case flow name for use inside a thread name, the same way Logback's
 * {@code %logger{N}} abbreviates a dotted logger name: segments (here, {@code -}-delimited
 * words) are truncated to their first character one at a time, left to right, until the whole
 * name fits the target length — the last segment, usually the most identifying part, is never
 * touched. A name that already fits is returned unchanged.
 *
 * <pre>
 * abbreviate("kitchen-processing-flow", 18) -&gt; "k-processing-flow"
 * abbreviate("dispatch-ingress-flow", 18)   -&gt; "d-ingress-flow"
 * abbreviate("order-generator-flow", 20)    -&gt; "order-generator-flow" (already fits)
 * </pre>
 */
public final class FlowNameAbbreviator {

    private static final int DEFAULT_MAX_LENGTH = 20;
    private static final String SEGMENT_DELIMITER = "-";

    private FlowNameAbbreviator() {
    }

    public static String abbreviate(String flowName) {
        return abbreviate(flowName, DEFAULT_MAX_LENGTH);
    }

    public static String abbreviate(String flowName, int maxLength) {
        if (flowName == null || flowName.length() <= maxLength) {
            return flowName;
        }
        final String[] segments = flowName.split(SEGMENT_DELIMITER);
        for (int i = 0; i < segments.length - 1; i++) {
            segments[i] = segments[i].substring(0, 1);
            if (String.join(SEGMENT_DELIMITER, segments).length() <= maxLength) {
                break;
            }
        }
        return String.join(SEGMENT_DELIMITER, segments);
    }

}
