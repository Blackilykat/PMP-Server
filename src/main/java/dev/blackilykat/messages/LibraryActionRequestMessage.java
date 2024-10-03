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
import dev.blackilykat.Storage;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageInvalidContentsException;

/**
 * Used when a client needs to receive missing actions from the server.
 * Clients can expect the server to send all library actions from (inclusive) {@link #start} up to the latest one.
 */
public class LibraryActionRequestMessage extends Message {
    public static final String MESSAGE_TYPE = "LIBRARY_ACTION_REQUEST";
    public int start;

    public LibraryActionRequestMessage(int start) {
        this.start = start;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("start", start);
    }

    @Override
    public void handle(Client client) {
        int currentActionId = Storage.getCurrentActionID();
        System.out.printf("DEBUG %d %d\n", currentActionId, start);
        if(start > currentActionId) {
            client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, this.messageId, String.format("Requested action #%d, but the latest is #%d", start, currentActionId-1));
            return;
        }
        for(int i = start; i < currentActionId; i++) {
            System.out.printf("DEEZ BUGS %d\n", i);
            client.send(Storage.actions.get(i).toMessage());
        }
    }

    //@Override
    public static LibraryActionRequestMessage fromJson(JsonObject json) throws MessageException {
        return new LibraryActionRequestMessage(json.get("start").getAsInt());
    }
}
