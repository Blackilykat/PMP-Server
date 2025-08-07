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

import static dev.blackilykat.Main.LOGGER;

public class LoginMessage extends Message {
    public static final String MESSAGE_TYPE = "LOGIN";

    // This message either contains
    public String password = null;
    public String hostname = null;
    // or
    public String token = null;
    public int deviceId = -1;
    // or password and deviceId to reset the token

    public LoginMessage(String password, String hostname) {
        this.password = password;
        this.hostname = hostname;
    }

    public LoginMessage(String tokenOrPassword, int deviceId, boolean isToken) {
        if(isToken) {
            this.token = tokenOrPassword;
        } else {
            this.password = tokenOrPassword;
        }
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
        } else {
            object.addProperty("token", token);
        }

        if(hostname != null) {
            object.addProperty("hostname", hostname);
        }

        if(deviceId != -1) {
            object.addProperty("deviceId", deviceId);
        }
    }

    @Override
    public void handle(Client client) {
        if(client.loginStage != LoginStage.LOGGED_OUT) {
            LOGGER.error("A client tried to log in while not logged out!");
            client.sendError(ErrorMessage.ErrorID.INVALID_CLIENT_STATE, this.messageId);
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
                client.sendError(ErrorMessage.ErrorID.LOGIN_INVALID_PASSWORD, messageId);
                return;
            }

            if(deviceId == -1){
                device = new Device(hostname);
                Storage.devices.put(device.id, device);
            } else {
                if((device = Storage.devices.get(deviceId)) == null) {

                    client.sendError(ErrorMessage.ErrorID.LOGIN_DEVICE_DOES_NOT_EXIST, messageId);
                    return;
                }
            }
        } else if(deviceId != -1 && token != null) {
            if((device = Storage.devices.get(deviceId)) == null) {
                client.sendError(ErrorMessage.ErrorID.LOGIN_DEVICE_DOES_NOT_EXIST, messageId);
                return;
            }
            if(device.token == null || !device.token.equals(token)) {
                client.loginStage = LoginStage.LOGGED_OUT;
                synchronized(client.loginLock) {
                    client.loginLock.notifyAll();
                }
                client.sendError(ErrorMessage.ErrorID.LOGIN_INVALID_TOKEN, messageId);
                return;
            }
        } else {
            client.sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, messageId, "Invalid combination of password, hostname, device id and token");
            return;
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
            LOGGER.error("Failed to send library hashes message", e);
        }

        client.loginStage = LoginStage.LOGGED_IN;
        synchronized(client.loginLock) {
            client.loginLock.notifyAll();
        }
    }

    //@Override
    public static Message fromJson(JsonObject json) throws MessageException {
        if(json.has("password")) {
            if(json.has("hostname")) {
                return new LoginMessage(json.get("password").getAsString(), json.get("hostname").getAsString());
            } else if(json.has("deviceId")) {
                return new LoginMessage(json.get("password").getAsString(), json.get("deviceId").getAsInt(), false);
            }
            throw new MessageMissingContentsException("Got password but neither hostname or device id");
        } else if(json.has("token")) {
            if(!json.has("deviceId")) {
                throw new MessageMissingContentsException("Missing device id");
            }
            return new LoginMessage(json.get("token").getAsString(), json.get("deviceId").getAsInt(), true);
        }
        throw new MessageMissingContentsException("Missing both password and token");
    };
}
