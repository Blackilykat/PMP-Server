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

import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.PlaybackSession;
import dev.blackilykat.messages.exceptions.MessageException;

import java.time.Instant;

/**
 * Updates an existing playback session which is known by both the server and the client.
 */
public class PlaybackSessionUpdateMessage extends Message {
    public static final String MESSAGE_TYPE = "PLAYBACK_SESSION_UPDATE";
    /**
     * A buffer message where updates should be stored. Used to avoid sending multiple updates when the information
     * could be packed into a single one.
     * If this is null, sessions should immediately create their own message and send it without interacting with this.
     * If this is not null, sessions should only store the updated info in here without sending anything.
     */
    public static PlaybackSessionUpdateMessage messageBuffer = null;

    public String track;
    public PlaybackSession.ShuffleOption shuffle;
    public PlaybackSession.RepeatOption repeat;
    public Boolean playing;
    public Integer position;
    public Integer owner;
    public Instant time;
    public int sessionId;


    /**
     * @param sessionId The session to update
     * @param track The new track that's playing, null if unchanged
     * @param shuffle The new shuffle option, null if unchanged
     * @param repeat The new repeat option, null if unchanged
     * @param playing Whether it's currently playing or not, null if unchanged
     * @param position The new position, null if unchanged (This should not be sent during normal progression of a track,
     *                 but only at jumps)
     * @param time When the update happened (prevents large de-syncs)
     */
    public PlaybackSessionUpdateMessage(int sessionId, String track, PlaybackSession.ShuffleOption shuffle, PlaybackSession.RepeatOption repeat, Boolean playing, Integer position, Integer owner, Instant time) {
        this.sessionId = sessionId;
        this.track = track;
        this.shuffle = shuffle;
        this.repeat = repeat;
        this.playing = playing;
        this.position = position;
        this.owner = owner;
        this.time = time;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("sessionId", sessionId);
        if(track != null) {
            object.addProperty("track", track);
        }
        if(shuffle != null) {
            object.addProperty("shuffle", shuffle.toString());
        }
        if(repeat != null) {
            object.addProperty("repeat", repeat.toString());
        }
        if(playing != null) {
            object.addProperty("playing", playing);
        }
        if(position != null) {
            object.addProperty("position", position);
        }
        if(owner != null) {
            object.addProperty("owner", owner);
        }
        if(time != null) {
            object.addProperty("time", time.toEpochMilli());
        }
    }

    @Override
    public void handle(Client client) {
        for(PlaybackSession session : PlaybackSession.getAvailableSessions()) {
            if(session.id != sessionId) continue;

            if(track != null) session.track = track;
            if(shuffle != null) session.shuffle = shuffle;
            if(repeat != null) session.repeat = repeat;
            if(playing != null) session.playing = playing;
            if(position != null) session.lastPositionUpdate = position;
            if(time != null) session.lastPositionUpdateTime = time;
            else session.lastPositionUpdateTime = Instant.now();
            if(owner != null) session.owner = owner;

            break;
        }
        Client.broadcastExcept(this, client.clientId);
    }

    //@Override
    public static PlaybackSessionUpdateMessage fromJson(JsonObject json) throws MessageException {
        return new PlaybackSessionUpdateMessage(
                json.get("sessionId").getAsInt(),
                json.has("track") ? json.get("track").getAsString() : null,
                json.has("shuffle") ? PlaybackSession.ShuffleOption.valueOf(json.get("shuffle").getAsString()) : null,
                json.has("repeat") ? PlaybackSession.RepeatOption.valueOf(json.get("repeat").getAsString()) : null,
                json.has("playing") ? json.get("playing").getAsBoolean() : null,
                json.has("position") ? json.get("position").getAsInt() : null,
                json.has("owner") ? json.get("owner").getAsInt() : null,
                json.has("time") ? Instant.ofEpochMilli(json.get("time").getAsLong()) : Instant.now()
        );
    }
}
