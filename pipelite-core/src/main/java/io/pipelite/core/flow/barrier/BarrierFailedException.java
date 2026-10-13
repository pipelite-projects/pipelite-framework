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
package io.pipelite.core.flow.barrier;

import java.util.List;

/**
 * A contribution that the {@code failedWhen} of a barrier selected: the barrier does not wait for the others,
 * and the correlation goes to the error handling of the flow with this exception.
 */
public class BarrierFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String barrierId;
    private final String key;
    private final String failedContributor;
    private final List<String> contributors;

    public BarrierFailedException(String barrierId, String key, String failedContributor, List<String> contributors) {
        super(String.format("Barrier '%s' failed for key '%s': the contribution of '%s' reported a failure (arrived so far: %s)",
            barrierId, key, failedContributor, contributors));
        this.barrierId = barrierId;
        this.key = key;
        this.failedContributor = failedContributor;
        this.contributors = List.copyOf(contributors);
    }

    public String getBarrierId() {
        return barrierId;
    }

    public String getKey() {
        return key;
    }

    public String getFailedContributor() {
        return failedContributor;
    }

    /** The contributors that had arrived, in arrival order, the failed one last. */
    public List<String> getContributors() {
        return contributors;
    }
}
