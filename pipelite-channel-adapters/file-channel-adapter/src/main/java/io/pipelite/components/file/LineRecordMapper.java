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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps newly appended file content into complete lines. A trailing line not yet
 * terminated by a line separator is left unconsumed, to be completed by a future read.
 */
public class LineRecordMapper implements FileRecordMapper<String> {

    @Override
    public MappingResult<String> map(String newContent) {

        if (newContent == null || newContent.isEmpty()) {
            return new MappingResult<>(Collections.emptyList(), 0L);
        }

        final int lastNewline = newContent.lastIndexOf('\n');
        if (lastNewline < 0) {
            return new MappingResult<>(Collections.emptyList(), 0L);
        }

        final String completePart = newContent.substring(0, lastNewline + 1);
        final String[] rawLines = completePart.split("\n", -1);

        final List<String> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            if (rawLine.endsWith("\r")) {
                rawLine = rawLine.substring(0, rawLine.length() - 1);
            }
            lines.add(rawLine);
        }
        // split(..., -1) leaves a trailing empty element for the delimiter that terminates completePart
        lines.remove(lines.size() - 1);

        return new MappingResult<>(lines, lastNewline + 1L);
    }

}
