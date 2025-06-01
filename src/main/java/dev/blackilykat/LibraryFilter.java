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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LibraryFilter implements Serializable {
    /**
     * The FLAC metadata key this filter is based on. Some examples can be <code>artist</code> and <code>album</code>.
     */
    public String key;
    /**
     * All options the user can select.
     */
    private List<LibraryFilterOption> options = new ArrayList<>();
    public PlaybackSession session;

    public LibraryFilter(PlaybackSession session, String key) {
        this.session = session;
        this.key = key;
    }

    public LibraryFilterOption[] getOptions() {
        return options.toArray(new LibraryFilterOption[0]);
    }

    public LibraryFilterOption getOption(String value) {
        for(LibraryFilterOption option : this.getOptions()) {
            if(option.value.equals(value)) {
                return option;
            }
        }
        return null;
    }

    public void setOptions(Collection<LibraryFilterOption> newOptions) {
        options.clear();
        options.addAll(newOptions);
    }
}
