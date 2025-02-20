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

package dev.blackilykat;

import dev.blackilykat.messages.LibraryActionMessage;

import java.io.Serializable;
import java.util.List;

public class LibraryAction implements Serializable {
    /**
     * The {@link LibraryActionMessage#actionId} of the pending action
     */
    public int actionId;
    /**
     * The {@link dev.blackilykat.Client#clientId} of the client who performed the action
     */
    public int clientId;
    /**
     * The name of the file the action is about
     */
    public String fileName;

    public Type actionType;
    public List<LibraryActionMessage.Pair<String, String>> newMetadata = null;

    public LibraryAction(int actionId, int clientId, String fileName, Type actionType) {
        this.actionId = actionId;
        this.clientId = clientId;
        this.fileName = fileName;
        this.actionType = actionType;
    }

    public LibraryAction(int actionId, int clientId, String fileName, Type actionType, List<LibraryActionMessage.Pair<String, String>> newMetadata) {
        // TODO check for correct initializer (ion feel like doing it rn)
        this.actionId = actionId;
        this.clientId = clientId;
        this.fileName = fileName;
        this.actionType = actionType;
        this.newMetadata = newMetadata;
    }

    public LibraryAction() {
        this.actionId = -1;
        this.clientId = -1;
        this.fileName = "";
    }

    public LibraryActionMessage toMessage() {
        if(actionType != Type.CHANGE_METADATA) {
            return new LibraryActionMessage(actionType, actionId, fileName);
        } else {
            return new LibraryActionMessage(actionType, actionId, fileName, newMetadata);
        }
    }

    public enum Type {
        /**
         * Add a new song to the library
         */
        ADD,
        /**
         * Remove a song from the library
         */
        REMOVE,
        /**
         * Replace the file of a song with another one (would be the same song, this action would only happen if like
         * someone changes the source, say, to get a higher quality version. This action exists so that when the
         * playback eventually gets tracked the counts don't get split or interrupted due to a file replacement)
         */
        REPLACE,
        /**
         * Change the metadata of a song while keeping the audio data untouched
         */
        CHANGE_METADATA
    }
}
