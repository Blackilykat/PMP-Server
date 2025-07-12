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

package dev.blackilykat.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.Json;
import dev.blackilykat.Storage;
import dev.blackilykat.Track;
import dev.blackilykat.messages.exceptions.MessageException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import static dev.blackilykat.Main.LOGGER;

/**
 * Sends every song's filename along with its crc32 hash. Used to make sure libraries dont get desynced, which would
 * ideally happen only if someone goes out of their way to manually edit music files not through the application. This
 * would also help discover and repair desync caused due to bugs though.
 */
public class LibraryHashesMessage extends Message {
    public static final String MESSAGE_TYPE = "LIBRARY_HASHES";
    Map<String, Long> hashes;

    public LibraryHashesMessage() {
        hashes = new HashMap<>();
    }

    public LibraryHashesMessage(Map<String, Long> hashes) {
        this.hashes = hashes;
    }

    public void add(String fileName, long hash) {
        hashes.put(fileName, hash);
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.add("hashes", Json.GSON.toJsonTree(hashes));
    }

    @Override
    public void handle(Client client) {
        //TODO
    }

    public static LibraryHashesMessage create() throws IOException {
        LibraryHashesMessage message = new LibraryHashesMessage();
        assert Storage.LIBRARY.exists() && Storage.LIBRARY.isDirectory();
        LOGGER.info("Calculating hashes for dir {}", Storage.LIBRARY.getAbsolutePath());
        List<Track> tracks = new LinkedList<>();
        int filesCached = 0;
        for (File file : Storage.LIBRARY.listFiles()) {
            String filename = file.getName();
            Track track;
            if(Storage.cachedTracks.containsKey(filename) && file.lastModified() == (track = Storage.cachedTracks.get(filename)).lastModified) {
                filesCached++;
            } else {
                LOGGER.warn("{} not cached!", filename);
                track = new Track(file);
            }
            message.add(filename, track.checksum);
            tracks.add(track);
        }
        LOGGER.info("{} tracks cached", filesCached);

        Storage.cachedTracks.clear();
        for(Track track : tracks) {
            Storage.cachedTracks.put(track.file.getName(), track);
        }
        return message;
    }

    //@Override
    public static LibraryHashesMessage fromJson(JsonObject json) throws MessageException {
        JsonObject hashes = json.get("hashes").getAsJsonObject();
        Map<String, Long> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : hashes.asMap().entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsLong());
        }
        return new LibraryHashesMessage(map);
    }
}
