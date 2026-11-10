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
package io.pipelite.core.exchange.internal;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * The node id of the identifiers can be set (issue #169), and two generators with different node ids never produce the
 * same identifier.
 */
public class DistributedIdentityGeneratorImplTest {

    private static final String PROPERTY = DistributedIdentityGeneratorImpl.NODE_ID_PROPERTY;

    private String previous;

    @Before
    public void saveProperty() {
        previous = System.getProperty(PROPERTY);
        System.clearProperty(PROPERTY);
    }

    @After
    public void restoreProperty() {
        if (previous == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, previous);
        }
    }

    /** The node id sits between the timestamp and the sequence: 10 bits above the 12 of the sequence. */
    private static int nodeIdOf(long id) {
        return (int) ((id >> 12) & 0x3FF);
    }

    @Test
    public void givenTheSystemProperty_thenTheNodeIdIsThatValue() {
        System.setProperty(PROPERTY, "321");

        final DistributedIdentityGeneratorImpl subject = new DistributedIdentityGeneratorImpl();

        Assert.assertTrue(subject.isNodeIdConfigured());
        Assert.assertEquals(321, subject.getNodeId());
        Assert.assertEquals(321, nodeIdOf(subject.nextId()));
    }

    @Test
    public void givenNothingConfigured_thenTheNodeIdIsDerivedFromTheHostAndInRange() {
        Assume.assumeTrue("PIPELITE_NODE_ID is set in this environment", System.getenv(DistributedIdentityGeneratorImpl.NODE_ID_ENV) == null);

        final DistributedIdentityGeneratorImpl subject = new DistributedIdentityGeneratorImpl();

        Assert.assertFalse(subject.isNodeIdConfigured());
        Assert.assertTrue(subject.getNodeId() >= 0 && subject.getNodeId() <= DistributedIdentityGeneratorImpl.maxNodeId());
    }

    @Test
    public void givenAPropertyOutOfRange_thenItFailsAndSaysWhere() {
        System.setProperty(PROPERTY, "1024");

        final IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, DistributedIdentityGeneratorImpl::new);

        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(PROPERTY));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("0 and 1023"));
    }

    @Test
    public void givenAPropertyThatIsNotANumber_thenItFails() {
        System.setProperty(PROPERTY, "node-1");

        final IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, DistributedIdentityGeneratorImpl::new);

        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("node-1"));
    }

    @Test
    public void givenABlankProperty_thenItIsAsIfItWereNotSet() {
        Assume.assumeTrue("PIPELITE_NODE_ID is set in this environment", System.getenv(DistributedIdentityGeneratorImpl.NODE_ID_ENV) == null);
        System.setProperty(PROPERTY, "  ");

        Assert.assertFalse(new DistributedIdentityGeneratorImpl().isNodeIdConfigured());
    }

    @Test
    public void givenTheSetter_thenTheNextIdentifiersCarryTheNewNodeId() {
        final DistributedIdentityGeneratorImpl subject = new DistributedIdentityGeneratorImpl();

        subject.setNodeId(77);

        Assert.assertTrue(subject.isNodeIdConfigured());
        Assert.assertEquals(77, nodeIdOf(subject.nextId()));
    }

    @Test
    public void givenANodeIdOutOfRange_thenTheSetterFailsAndKeepsTheOldOne() {
        final DistributedIdentityGeneratorImpl subject = new DistributedIdentityGeneratorImpl(5);

        Assert.assertThrows(IllegalArgumentException.class, () -> subject.setNodeId(-1));
        Assert.assertThrows(IllegalArgumentException.class, () -> subject.setNodeId(1024));

        Assert.assertEquals(5, subject.getNodeId());
    }

    @Test
    public void givenTwoGeneratorsWithDifferentNodeIds_thenTheyNeverProduceTheSameIdentifier() {
        final DistributedIdentityGeneratorImpl first = new DistributedIdentityGeneratorImpl(1);
        final DistributedIdentityGeneratorImpl second = new DistributedIdentityGeneratorImpl(2);
        final Set<Long> identifiers = new HashSet<>();

        for (int i = 0; i < 20_000; i++) {
            Assert.assertTrue(identifiers.add(first.nextId()));
            Assert.assertTrue(identifiers.add(second.nextId()));
        }
    }
}
