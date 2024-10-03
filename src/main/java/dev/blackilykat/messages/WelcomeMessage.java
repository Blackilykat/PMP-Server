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
import dev.blackilykat.messages.exceptions.MessageException;

/**
 * The first message the server sends when a client successfully connects (would be after authentication when that gets
 * implemented), used to confirm that it successfully connected and to communicate its
 * {@link dev.blackilykat.Client#clientId}
 */
public class WelcomeMessage extends Message {
    public static final String MESSAGE_TYPE = "WELCOME";
    /**
     * @see dev.blackilykat.Client#clientId
     */
    public int clientId;
    /**
     * The latest library action id so the client can catch up if needed before checking checksums
     */
    public int latestActionId;

    public WelcomeMessage(int clientId, int latestActionId) {
        if(clientId < 0) {
            throw new IllegalArgumentException("Client ID must be greater or equal than 0");
        }
        if(latestActionId < 0) {
            throw new IllegalArgumentException("latest action ID must be greater or equal than 0");
        }
        this.clientId = clientId;
        this.latestActionId = latestActionId;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("client_id", clientId);
        object.addProperty("latest_action_id", latestActionId);
    }

    @Override
    public void handle(Client client) {
        client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, messageId, "Clients cannot send welcome messages!");
    }

    //@Override
    public static Message fromJson(JsonObject json) throws MessageException {
        return new WelcomeMessage(json.get("client_id").getAsInt(), json.get("latest_action_id").getAsInt());
    }
}
