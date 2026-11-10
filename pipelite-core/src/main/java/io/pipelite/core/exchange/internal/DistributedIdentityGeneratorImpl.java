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

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

/**
 *
 * Generating unique IDs in a distributed environment at high scale
 * Inspired by Twitter snowflake: https://github.com/twitter/snowflake/tree/snowflake-2010
 *
 * This class should be used as a Singleton.
 * Make sure that you create and reuse a Single instance of SequenceGenerator per node in your distributed system cluster.
 *
 * @see https://www.callicoder.com/distributed-unique-id-sequence-number-generator/
 *
 */
public class DistributedIdentityGeneratorImpl implements IdentityGenerator {

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    /** The system property that sets the node id (issue #169); the environment variable is {@code PIPELITE_NODE_ID}. */
    public static final String NODE_ID_PROPERTY = "pipelite.node.id";
    public static final String NODE_ID_ENV = "PIPELITE_NODE_ID";

    private static final int MAX_NODE_ID = (int)(Math.pow(2, NODE_ID_BITS) - 1);
    private static final int MAX_SEQUENCE = (int)(Math.pow(2, SEQUENCE_BITS) - 1);

    // Custom Epoch (January 1, 2015 Midnight UTC = 2015-01-01T00:00:00Z)
    private static final long CUSTOM_EPOCH = 1420070400000L;

    private volatile int nodeId;
    private volatile boolean nodeIdConfigured;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    // Create SequenceGenerator with a nodeId
    public DistributedIdentityGeneratorImpl(int nodeId) {
        if(nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(String.format("NodeId must be between %d and %d", 0, MAX_NODE_ID));
        }
        this.nodeId = nodeId;
        this.nodeIdConfigured = true;
    }

    /**
     * The node id is the one set with the {@code pipelite.node.id} system property or the {@code PIPELITE_NODE_ID}
     * environment variable; when neither is set, it is derived from the network interfaces of the host, which two
     * instances can share (issue #169).
     */
    public DistributedIdentityGeneratorImpl() {
        final Integer configured = configuredNodeId();
        this.nodeIdConfigured = configured != null;
        this.nodeId = configured != null ? configured : createNodeId();
    }

    /**
     * Sets the node id, for the identifiers generated from now on. Two instances of an application must have
     * different ones: the identifiers they generate are then never the same.
     */
    public synchronized void setNodeId(int nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(String.format("NodeId must be between %d and %d", 0, MAX_NODE_ID));
        }
        this.nodeId = nodeId;
        this.nodeIdConfigured = true;
    }

    public int getNodeId() {
        return nodeId;
    }

    /** Whether the node id was set, with the property, the variable or the setter, and not derived from the host. */
    public boolean isNodeIdConfigured() {
        return nodeIdConfigured;
    }

    /** The largest node id there can be: the node id has ten bits. */
    public static int maxNodeId() {
        return MAX_NODE_ID;
    }

    private static Integer configuredNodeId() {
        String value = System.getProperty(NODE_ID_PROPERTY);
        String source = "the system property " + NODE_ID_PROPERTY;
        if (value == null || value.isBlank()) {
            value = System.getenv(NODE_ID_ENV);
            source = "the environment variable " + NODE_ID_ENV;
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(String.format(
                "The node id in %s is '%s': it must be a whole number between %d and %d", source, value, 0, MAX_NODE_ID));
        }
        if (parsed < 0 || parsed > MAX_NODE_ID) {
            throw new IllegalArgumentException(String.format(
                "The node id in %s is %d: it must be between %d and %d", source, parsed, 0, MAX_NODE_ID));
        }
        return parsed;
    }

    @Override
    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if(currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Invalid System Clock!");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if(sequence == 0) {
                // Sequence Exhausted, wait till next millisecond.
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            // reset sequence to start with zero for the next millisecond
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        long id = currentTimestamp << (NODE_ID_BITS + SEQUENCE_BITS);
        id |= ((long) nodeId << SEQUENCE_BITS);
        id |= sequence;
        return id;
    }


    // Get current timestamp in milliseconds, adjust for the custom epoch.
    private static long timestamp() {
        return Instant.now().toEpochMilli() - CUSTOM_EPOCH;
    }

    // Block and wait till next millisecond
    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }

    private int createNodeId() {

        int result;
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while ((networkInterfaces).hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                }
            }
            result = sb.toString().hashCode();
        } catch (Exception ex) {
            result = (new SecureRandom().nextInt());
        }
        result = result & MAX_NODE_ID;
        return result;

    }

    @Override
    public String nextIdAsText() {
        return Long.toHexString(nextId());
    }

    @Override
    public String nextIdAsText(int maxLenght) {
        final String nextIdAsText = nextIdAsText();
        if(nextIdAsText.length() > maxLenght){
            return nextIdAsText.substring(0, maxLenght - 1);
        }
        return nextIdAsText;
    }
}

