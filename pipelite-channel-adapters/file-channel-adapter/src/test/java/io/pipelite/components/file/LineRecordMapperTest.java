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
package io.pipelite.components.file;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class LineRecordMapperTest {

    private final LineRecordMapper subject = new LineRecordMapper();

    @Test
    public void shouldMapSingleCompleteLine() {
        final MappingResult<String> result = subject.map("hello\n");
        Assert.assertEquals(List.of("hello"), result.getRecords());
        Assert.assertEquals(6L, result.getConsumedLength());
    }

    @Test
    public void shouldNotConsumePartialTrailingLine() {
        final MappingResult<String> result = subject.map("hello\nworld");
        Assert.assertEquals(List.of("hello"), result.getRecords());
        Assert.assertEquals(6L, result.getConsumedLength());
    }

    @Test
    public void shouldMapMultipleLines() {
        final MappingResult<String> result = subject.map("a\nb\nc\n");
        Assert.assertEquals(List.of("a", "b", "c"), result.getRecords());
        Assert.assertEquals(6L, result.getConsumedLength());
    }

    @Test
    public void shouldMapEmptyLinesInTheMiddle() {
        final MappingResult<String> result = subject.map("a\n\nb\n");
        Assert.assertEquals(List.of("a", "", "b"), result.getRecords());
        Assert.assertEquals(5L, result.getConsumedLength());
    }

    @Test
    public void shouldReturnEmptyResultForEmptyString() {
        final MappingResult<String> result = subject.map("");
        Assert.assertTrue(result.getRecords().isEmpty());
        Assert.assertEquals(0L, result.getConsumedLength());
    }

    @Test
    public void shouldReturnEmptyResultWhenNoTerminatorPresent() {
        final MappingResult<String> result = subject.map("no terminator here");
        Assert.assertTrue(result.getRecords().isEmpty());
        Assert.assertEquals(0L, result.getConsumedLength());
    }

    @Test
    public void shouldTrimCarriageReturnBeforeLineFeed() {
        final MappingResult<String> result = subject.map("hello\r\nworld\r\n");
        Assert.assertEquals(List.of("hello", "world"), result.getRecords());
        Assert.assertEquals(14L, result.getConsumedLength());
    }

    @Test
    public void shouldMapSingleCrLfOnlyLine() {
        final MappingResult<String> result = subject.map("\r\n");
        Assert.assertEquals(List.of(""), result.getRecords());
        Assert.assertEquals(2L, result.getConsumedLength());
    }

}
