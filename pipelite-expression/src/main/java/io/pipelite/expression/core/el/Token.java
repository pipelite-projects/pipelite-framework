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
package io.pipelite.expression.core.el;

public class Token {

    private TokenType type;

    private String surface;

    private int pos;

    public Token() {
        this.surface = new String();
    }

    public TokenType getType() {
        return type;
    }

    public void setType(TokenType type) {
        this.type = type;
    }

    public boolean sameTypeOf(TokenType tokenType) {
        return this.type == tokenType;
    }

    public String getSurface() {
        return surface;
    }

    public void setSurface(String surface) {
        this.surface = surface;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public void append(char c) {
        surface += c;
    }

    public int indexOf(char c) {
        return surface.indexOf(c);
    }

    public boolean containsChar(char c) {
        return indexOf(c) > -1;
    }

    public boolean containsChar(CharSequence chars) {
        for (int i = 0; i < chars.length(); i++) {
            if (containsChar(chars.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public void append(String s) {
        surface += s;
    }

    public char charAt(int pos) {
        return surface.charAt(pos);
    }

    public int length() {
        return surface.length();
    }

    @Override
    public String toString() {
        return surface;
    }

}
