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
import dev.blackilykat.Device;
import dev.blackilykat.LoginStage;
import dev.blackilykat.Main;
import dev.blackilykat.PlaybackSession;
import dev.blackilykat.Storage;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageMissingContentsException;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.time.Instant;

public class LoginMessage extends Message {
    public static final String MESSAGE_TYPE = "LOGIN";

    // This message either contains
    public String password = null;
    public String hostname = null;
    // or
    public String token = null;
    public int deviceId = -1;

    public LoginMessage(String password, String hostname) {
        this.password = password;
        this.hostname = hostname;
    }

    public LoginMessage(String token, int deviceId) {
        this.token = token;
        this.deviceId = deviceId;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        if(password != null) {
            object.addProperty("password", password);
            object.addProperty("hostname", hostname);
        } else {
            object.addProperty("token", token);
            object.addProperty("deviceId", deviceId);
        }
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

        Device device;
        if(password != null) {
            String hashedPassword = Storage.getPassword();
            assert hashedPassword != null;
            boolean matches = BCrypt.checkpw(password, hashedPassword);
            if(!matches) {
                client.loginStage = LoginStage.LOGGED_OUT;
                synchronized(client.loginLock) {
                    client.loginLock.notifyAll();
                }
                client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, messageId, "Invalid password");
                return;
            }

            device = new Device(hostname);
            Storage.devices.put(device.id, device);
        } else {
            device = Storage.devices.get(deviceId);
            if(!device.token.equals(token)) {
                client.loginStage = LoginStage.LOGGED_OUT;
                synchronized(client.loginLock) {
                    client.loginLock.notifyAll();
                }
                client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, messageId, "Invalid token");
                return;
            }
        }
        device.connectedClient = client;
        client.device = device;
        device.lastLogin = Instant.now();

        // Always regenerate token for better security
        device.token = Device.generateToken();

        Main.clients.add(client);

        client.send(new WelcomeMessage(client.clientId, Storage.getCurrentActionID(), device.token, device.id));
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
        if(json.has("password")) {
            return fromJsonWithPassword(json);
        } else if(json.has("token")) {
            return fromJsonWithToken(json);
        }
        throw new MessageMissingContentsException("Missing both password and token");
    };

    private static LoginMessage fromJsonWithPassword(JsonObject json) throws MessageException {
        if(!json.has("password") || !json.has("hostname")) {
            throw new MessageMissingContentsException("Missing password or hostname");
        }
        return new LoginMessage(json.get("password").getAsString(), json.get("hostname").getAsString());
    }
    private static LoginMessage fromJsonWithToken(JsonObject json) throws MessageException {
        if(!json.has("token") || !json.has("deviceId")) {
            throw new MessageMissingContentsException("Missing token or id");
        }
        return new LoginMessage(json.get("token").getAsString(), json.get("deviceId").getAsInt());
    }
}
