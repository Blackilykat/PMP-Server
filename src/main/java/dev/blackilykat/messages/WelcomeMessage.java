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
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageMissingContentsException;

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
    public String token;
    public int deviceId;

    public WelcomeMessage(int clientId, int latestActionId, String token, int deviceId) {
        if(clientId < 0) {
            throw new IllegalArgumentException("Client ID must be greater or equal than 0");
        }
        if(latestActionId < 0) {
            throw new IllegalArgumentException("latest action ID must be greater or equal than 0");
        }
        if(token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if(deviceId <= 0) {
            throw new IllegalArgumentException("device ID must be greater than 0");
        }
        this.clientId = clientId;
        this.latestActionId = latestActionId;
        this.token = token;
        this.deviceId = deviceId;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("client_id", clientId);
        object.addProperty("latest_action_id", latestActionId);
        object.addProperty("token", token);
        object.addProperty("device_id", deviceId);
    }

    @Override
    public void handle(Client client) {
        client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, messageId, "Clients cannot send welcome messages!");
    }

    //@Override
    public static Message fromJson(JsonObject json) throws MessageException {
        if(!json.has("client_id")) {
            throw new MessageMissingContentsException("Missing client id");
        }
        if(!json.has("latest_action_id")) {
            throw new MessageMissingContentsException("Missing latest action id");
        }
        if(!json.has("token")) {
            throw new MessageMissingContentsException("Missing token");
        }
        if(!json.has("device_id")) {
            throw new MessageMissingContentsException("Missing device id");
        }
        return new WelcomeMessage(
                json.get("client_id").getAsInt(),
                json.get("latest_action_id").getAsInt(),
                json.get("token").getAsString(),
                json.get("device_id").getAsInt()
        );
    }
}
