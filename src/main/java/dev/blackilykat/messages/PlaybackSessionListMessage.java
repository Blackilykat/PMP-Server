/*
 * PMP-Server - A server for Personal Music Platform, a self-hosted
 * platform to play music and make sure everything is always synced
 * across devices.
 * Copyright (C) 2024 Blackilykat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.blackilykat.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.PlaybackSession;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageInvalidContentsException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        public PlaybackSessionElement(int id, String track, PlaybackSession.ShuffleOption shuffle, PlaybackSession.RepeatOption repeat,
                                      boolean playing, int lastPositionUpdate, int owner, Instant lastUpdateTime) {
            this.id = id;
            this.track = track;
            this.shuffle = shuffle;
            this.repeat = repeat;
            this.playing = playing;
            this.lastPositionUpdate = lastPositionUpdate;
            this.owner = owner;
            this.lastUpdateTime = lastUpdateTime;
        }

        public PlaybackSessionElement(PlaybackSession session) {
            this(session.id, session.track, session.shuffle, session.repeat, session.playing, session.lastPositionUpdate, session.owner, session.lastPositionUpdateTime);
        }
    }
}
