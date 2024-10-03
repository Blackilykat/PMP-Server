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
 * Used to indicate that a side is disconnecting from the socket. The side receiving this message can expect the side
 * who sent it to have already disconnected from the socket.
 */
public class DisconnectMessage extends Message {
    public static final String MESSAGE_TYPE = "DISCONNECT";
    /**
     * Interval in which the client will attempt to reconnect in seconds. If negative, it will not be included in the
     * json message which indicates the client should not attempt to automatically reconnect.
     */
    public int reconnectIn = -1;

    public DisconnectMessage() {}

    public DisconnectMessage(int reconnectIn) {
        this.reconnectIn = reconnectIn;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        if(reconnectIn >= 0) {
            object.addProperty("reconnect_in", reconnectIn);
        }
    }

    @Override
    public void handle(Client client) {
        client.disconnect();
    }

    //@Override
    public static DisconnectMessage fromJson(JsonObject json) throws MessageException {
        if(json.has("reconnect_in")) {
            return new DisconnectMessage(json.get("reconnect_in").getAsInt());
        } else {
            return new DisconnectMessage();
        }
    }

}
