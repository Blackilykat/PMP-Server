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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.Main;
import dev.blackilykat.Pair;
import dev.blackilykat.Storage;
import dev.blackilykat.Triple;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageInvalidContentsException;

import java.util.ArrayList;
import java.util.List;

public class DataHeaderListMessage extends Message {
    public static final String MESSAGE_TYPE = "DATA_HEADER_LIST";

    public List<Triple<Integer, String, String>> headers = new ArrayList<>();

    public DataHeaderListMessage() {
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        JsonArray arr = new JsonArray();
        for(Triple<Integer, String, String> header : headers) {
            // could make it a key/value pair using JSON keys and values, but it's a mess to deal with that, and it would
            // prevent having duplicate headers which - while it would make little sense - should be perfectly valid
            JsonObject obj = new JsonObject();
            obj.addProperty("id", header.a);
            obj.addProperty("key", header.b);
            obj.addProperty("name", header.c);
            arr.add(obj);
        }
        object.add("headers", arr);
    }

    public static Message fromJson(JsonObject json) throws MessageException {
        DataHeaderListMessage msg = new DataHeaderListMessage();
        try {
            for(JsonElement headerElem : json.getAsJsonArray("headers")) {
                JsonObject header = (JsonObject) headerElem;
                msg.headers.add(new Triple<>(header.get("id").getAsInt(), header.get("key").getAsString(), header.get("name").getAsString()));
            }
        } catch(UnsupportedOperationException | ClassCastException e) {
            throw new MessageInvalidContentsException("Expected list of strings, found something else");
        }
        return msg;
    }

    @Override
    public void handle(Client connection) {
        Client.broadcastExcept(this, connection.clientId);
        Storage.setTrackDataHeaders(headers);
    }
}
