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
import dev.blackilykat.Main;
import dev.blackilykat.messages.exceptions.MessageException;

import java.util.TimerTask;

public class KeepAliveMessage extends Message {
    public static final String MESSAGE_TYPE = "KEEPALIVE";
    public static final long KEEPALIVE_MS = 10_000;
    public static final long KEEPALIVE_MAX_MS = 30_000;

    public KeepAliveMessage() {}

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {}

    @Override
    public void handle(Client client) {
        if(client.keepaliveKillTask != null) {
            client.keepaliveKillTask.cancel();
        }

        client.keepaliveKillTask = makeKillTask(client);

        Client.timer.schedule(client.keepaliveKillTask, KEEPALIVE_MAX_MS);
    }

    // could avoid, but I'm keeping this here for consistency's sake (and for "lack of abstract static" copium)
    //@Override
    public static KeepAliveMessage fromJson(JsonObject json) throws MessageException {
        return new KeepAliveMessage();
    }

    public static TimerTask makeKillTask(Client client) {
        return new TimerTask() {
            @Override
            public void run() {
                Main.LOGGER.warn("Disconnecting client #{} due to lack of keepalive messages", client.clientId);
                client.send(new DisconnectMessage(10));
                client.disconnect();
            }
        };
    }
}
