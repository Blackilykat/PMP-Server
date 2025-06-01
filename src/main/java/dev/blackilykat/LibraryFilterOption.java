/*
 * Copyright (C) 2025 Blackilykat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.blackilykat;

import java.io.Serializable;

public class LibraryFilterOption implements Comparable<LibraryFilterOption>, Serializable {
    public final LibraryFilter filter;
    public final String value;
    public State state = State.NONE;

    public LibraryFilterOption(LibraryFilter filter, String value) {
        System.out.println("adding option with value " + value + " to filter " + filter.key);
        this.filter = filter;
        this.value = value;
    }

    // so they can be sorted in a stream
    @Override
    public int compareTo(LibraryFilterOption o) {
        return value.compareTo(o.value);
    }

    @Override
    public int hashCode() {
        return filter.hashCode() + value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof LibraryFilterOption option)) return false;
        // yes the == is intentional it must be the same object
        return filter == option.filter && value.equals(option.value);
    }

    public enum State {
        NONE,
        POSITIVE,
        NEGATIVE
    }
}
