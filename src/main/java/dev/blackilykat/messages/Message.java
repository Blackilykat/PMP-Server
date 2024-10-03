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
import dev.blackilykat.Json;
import dev.blackilykat.messages.exceptions.MessageException;

public abstract class Message implements Cloneable {
    /**
     * An increasing identifier which should be unique per session (as in every connection will have its own counter)
     */
    public int messageId = -1;

    /**
     * Returns a message identical to this one but with a new {@link #messageId}.
     * Does not change the value on this object.
     * @param messageId the new messageId value
     * @return A copy of the object with the updated message id
     */
    public Message withMessageId(int messageId) {
        Message copy = this.clone();
        copy.messageId = messageId;
        return copy;
    }

    /**
     * @return The type of the message. Should be in all caps, use underscores instead of spaces and be unique.
     */
    public abstract String getMessageType();

    /**
     * Fills the provided {@link JsonObject} with all the information specific to that message type (this means the
     * type itself and the message id are excluded)
     */
    public abstract void fillContents(JsonObject object);

    public abstract void handle(Client client);

    public String toJson() {
        if(messageId < 0) {
            throw new IllegalStateException("The message ID is a negative value ("+messageId+")! Did you forget to set it?");
        }
        JsonObject object = new JsonObject();
        object.addProperty("message_type", getMessageType());
        object.addProperty("message_id", messageId);
        fillContents(object);
        return Json.toJson(object);
    }

    /**
     * (pretend this is abstract static) (copium) (probably horrible practice but ughhh)
     * @param json The json representation of the message
     * @return The message object. This should always be of the same type as the class it is implemented in.
     * @see #withMessageId(int)
     */
    public static Message fromJson(JsonObject json) throws MessageException {
        throw new UnsupportedOperationException("fromJson(JsonObject) should not be called from the base Message class!");
    };

    /**
     * Creates an identical copy of this message (without the {@link #messageId})
     */
    @Override
    public Message clone() {
        try {
            Message clone = (Message) super.clone();
            clone.messageId = -1;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
