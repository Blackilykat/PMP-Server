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
import dev.blackilykat.LoginStage;
import dev.blackilykat.Main;
import dev.blackilykat.PlaybackSession;
import dev.blackilykat.Storage;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageMissingContentsException;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;

public class LoginMessage extends Message {
    public static final String MESSAGE_TYPE = "LOGIN";

    public String password;

    public LoginMessage(String password) {
        this.password = password;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("password", password);
    }

    @Override
    public void handle(Client client) {
        if(client.loginStage != LoginStage.LOGGED_OUT) {
            client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, this.messageId, "Can't log in while not logged out");
            return;
        }

        client.loginStage = LoginStage.PROCESSING;
        synchronized(client.loginLock) {
            client.loginLock.notifyAll();
        }

        String hashedPassword = Storage.getPassword();
        assert hashedPassword != null;
        boolean matches = BCrypt.checkpw(password, hashedPassword);
        if(!matches) {
            client.loginStage = LoginStage.LOGGED_OUT;
            synchronized(client.loginLock) {
                client.loginLock.notifyAll();
            }
            return;
        }

        Main.clients.add(client);

        client.send(new WelcomeMessage(client.clientId, Storage.getCurrentActionID()));
        Client.broadcast(new TestMessage(client.clientId));
        DataHeaderListMessage headersMsg = new DataHeaderListMessage();
        headersMsg.headers.addAll(Storage.getTrackDataHeaders());
        client.send(headersMsg);
        client.send(new LatestHeaderIdMessage(Storage.getLatestHeaderId()));
        client.send(new PlaybackSessionListMessage(PlaybackSession.getAvailableSessions()));

        try {
            client.send(LibraryHashesMessage.create());
        } catch(IOException e) {
            e.printStackTrace();
        }

        client.loginStage = LoginStage.LOGGED_IN;
        synchronized(client.loginLock) {
            client.loginLock.notifyAll();
        }
    }

    //@Override
    public static Message fromJson(JsonObject json) throws MessageException {
        if(!json.has("password")) {
            throw new MessageMissingContentsException("Missing password");
        }
        return new LoginMessage(json.get("password").getAsString());
    };
}
