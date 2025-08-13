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

import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.PlaybackSession;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageMissingContentsException;

/**
 * Message to delete a playback session.
 */
public class PlaybackSessionDeleteMessage extends Message {
    public static final String MESSAGE_TYPE = "PLAYBACK_SESSION_DELETE";
    int id;

    public PlaybackSessionDeleteMessage(int id) {
        this.id = id;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("id", id);
    }

    @Override
    public void handle(Client client) {
        for(PlaybackSession session : PlaybackSession.getAvailableSessions()) {
            if(session.id == id) {
                session.unregister();
                break;
            }
        }
        Client.broadcastExcept(this, client.clientId);
    }

    //@Override
    public static PlaybackSessionDeleteMessage fromJson(JsonObject json) throws MessageException {
        if(!json.has("id")) throw new MessageMissingContentsException("Missing id");
        return new PlaybackSessionDeleteMessage(json.get("id").getAsInt());
    }
}
