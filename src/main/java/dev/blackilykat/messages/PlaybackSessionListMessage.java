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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.Json;
import dev.blackilykat.LibraryFilter;
import dev.blackilykat.LibraryFilterOption;
import dev.blackilykat.Pair;
import dev.blackilykat.PlaybackSession;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageInvalidContentsException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains a list of all sessions that currently exist. When the server sends this, clients must ensure their session
 * list is identical to the one contained in this message, whether that's by removing, adding or modifying its existing
 * session list. If clients need a session to keep existing regardless of whether it was previously on the server,
 * they can keep it and send a {@link PlaybackSessionCreateMessage}, ensuring there is no mismatching information
 * between the client and the server.
 */
public class PlaybackSessionListMessage extends Message {
    public static final String MESSAGE_TYPE = "PLAYBACK_SESSION_LIST";
    List<PlaybackSessionElement> sessions = new ArrayList<>();

    public PlaybackSessionListMessage(PlaybackSession... sessions) {
        for(PlaybackSession session : sessions) {
            this.sessions.add(new PlaybackSessionElement(session));
        }
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        JsonArray arr = new JsonArray();
        for(PlaybackSessionElement session : sessions) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", session.id);
            obj.addProperty("track", session.track);
            obj.addProperty("shuffle", session.shuffle.toString());
            obj.addProperty("repeat", session.repeat.toString());
            obj.addProperty("playing", session.playing);
            obj.addProperty("lastPositionUpdate", session.lastPositionUpdate);
            if(session.lastUpdateTime != null) {
                obj.addProperty("lastUpdateTime", session.lastUpdateTime.toEpochMilli());
            }
            obj.addProperty("owner", session.owner);
            obj.add("filters", Json.GSON.toJsonTree(session.filters));

            arr.add(obj);
        }
        object.add("sessions", arr);
    }

    @Override
    public void handle(Client client) {
    }

    //@Override
    public static Message fromJson(JsonObject json) throws MessageException {
        throw new MessageInvalidContentsException();
    }

    private static class PlaybackSessionElement {
        public int id;
        public String track;
        public PlaybackSession.ShuffleOption shuffle;
        public PlaybackSession.RepeatOption repeat;
        public boolean playing;
        public int lastPositionUpdate;
        public int owner;
        public Instant lastUpdateTime;
        public List<Pair<String, List<Pair<String, LibraryFilterOption.State>>>> filters;

        public PlaybackSessionElement(int id, String track, PlaybackSession.ShuffleOption shuffle, PlaybackSession.RepeatOption repeat,
                                      boolean playing, int lastPositionUpdate, int owner, List<Pair<String, List<Pair<String, LibraryFilterOption.State>>>> filters, Instant lastUpdateTime) {
            this.id = id;
            this.track = track;
            this.shuffle = shuffle;
            this.repeat = repeat;
            this.playing = playing;
            this.lastPositionUpdate = lastPositionUpdate;
            this.owner = owner;
            this.filters = filters;
            this.lastUpdateTime = lastUpdateTime;
        }

        public PlaybackSessionElement(PlaybackSession session) {
            this(session.id, session.track, session.shuffle, session.repeat, session.playing, session.lastPositionUpdate, session.owner, PlaybackSessionUpdateMessage.getFiltersFromSession(session), session.lastPositionUpdateTime);
        }

    }
}
