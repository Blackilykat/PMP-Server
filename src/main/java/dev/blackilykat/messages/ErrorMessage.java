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
import dev.blackilykat.messages.exceptions.MessageInvalidContentsException;

/**
 * Used to communicate that an unexpected error of any kind has occurred.
 */
public class ErrorMessage extends Message {
    public static final String MESSAGE_TYPE = "ERROR";
    /**
     * Human-readable information about the error (could be a stacktrace/exception name for {@link ErrorType#SERVER})
     * <br />If empty, it is not included in the message sent to the client.
     */
    public String info = "";

    /**
     * How long the client should wait before retrying something.
     * <br />If negative, it is not included in the message sent to the client.
     * @see Action#RETRY
     * @see Action#RECONNECT
     */
    public int secondsToRetry = -1;

    /**
     * If the error was triggered by a message, the {@link Message#messageId} of said message.
     * <br />If negative, it is not included in the message sent to the client.
     */
    public int relativeToMessage = -1;

    public final ErrorType errorType;
    public final Action action;

    public ErrorMessage(ErrorType errorType) {
        this.errorType = errorType;
        this.action = Action.UNKNOWN;
    }
    public ErrorMessage(ErrorType errorType, Action action) {
        this.errorType = errorType;
        this.action = action;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        if(info != null && !info.isEmpty()) object.addProperty("info",  info);
        if(secondsToRetry >= 0) object.addProperty("seconds_to_retry", secondsToRetry);
        if(relativeToMessage >= 0) object.addProperty("relative_to_message", relativeToMessage);
        object.addProperty("error_type", errorType.toString());
        object.addProperty("action", action.toString());
    }

    @Override
    public void handle(Client client) {
    }

    //@Override
    public static Message fromJson(JsonObject json) throws MessageException {
        throw new MessageInvalidContentsException();
    }

    /**
     * Generally, what caused the error
     */
    public enum ErrorType {
        /**
         * An error occurred internally in the server, there is no further information available (but there may be an
         * explanation
         */
        SERVER,
        /**
         * The client sent an incorrectly formatted message.
         */
        MESSAGE_FORMAT,
        /**
         * The client sent a correctly formatted message with non-missing invalid contents.
         */
        MESSAGE_INVALID_CONTENTS,
        /**
         * The client sent a correctly formatted message with missing contents.
         */
        MESSAGE_MISSING_CONTENTS,
        /**
         * The client tried to something that cannot be done at the moment (for example, adding a file to the library
         * while another client is already doing that). Ideally paired with {@link Action#RETRY} and
         * {@link #secondsToRetry}
         */
        BUSY
    }

    /**
     * How the client should behave after receiving this message.
     */
    public enum Action {
        /**
         * The client can decide how to react (may be a popup, automatic report, ignoring...)
         */
        UNKNOWN,
        /**
         * The client should retry doing what it was trying to do in {@link #secondsToRetry} seconds. This should only
         * be sent if the error was triggered by a message sent by the client.
         */
        RETRY,
        /**
         * The client should disconnect from the server without automatically reconnecting
         */
        DISCONNECT,
        /**
         * The client should disconnect and try to reconnect at intervals of {@link #secondsToRetry} seconds
         */
        RECONNECT
    }
}
