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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.Json;
import dev.blackilykat.Storage;
import dev.blackilykat.messages.exceptions.MessageException;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to notify of changes in the library. For ADD and REPLACE, the server should wait about 10 seconds for a
 * connection to be made to the http server so the clients can upload their files. Clients can expect a successful
 * transfer if the http server sends back a 200. When clients get an ADD or REPLACE message from the server they can
 * rely on the http server to get the file as well.
 */
public class LibraryActionMessage extends Message {
    public static final String MESSAGE_TYPE = "LIBRARY_ACTION";
    /**
     * The pending action, not a list because I want to play it safe and not allow multiple changes at the same time but
     * that might change in the future.
     */
    public static PendingAction pendingAction = new PendingAction();
    public int actionId;
    public Type actionType;
    public String fileName;
    public List<Pair<String, String>> newMetadata;

    public LibraryActionMessage(Type type, int actionId, String fileName) {
        if(type == Type.CHANGE_METADATA) {
            throw new IllegalArgumentException("Incorrect initializer: expected List<Pair<String, String>> as fourth argument for action type " + type);
        }
        this.actionType = type;
        this.actionId = actionId;
        this.fileName = fileName;
    }

    public LibraryActionMessage(Type type, int actionId, String fileName, List<Pair<String, String>> newMetadata) {
        if(type != Type.CHANGE_METADATA) {
            throw new IllegalArgumentException("Incorrect initializer: expected only three arguments for action type " + type);
        }
        this.actionType = type;
        this.actionId = actionId;
        this.fileName = fileName;
        this.newMetadata = newMetadata;
    }

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void fillContents(JsonObject object) {
        object.addProperty("action_type", actionType.toString());
        object.addProperty("action_id", actionId);
        object.addProperty("file_name", fileName);
        if(actionType == Type.CHANGE_METADATA) {
            object.add("new_metadata", Json.GSON.toJsonTree(newMetadata));
        }
    }

    //@Override
    public static LibraryActionMessage fromJson(JsonObject json) throws MessageException {
        Type type = Type.valueOf(json.get("action_type").getAsString());
        if(type == Type.CHANGE_METADATA) {
            List<Pair<String, String>> metadata = new ArrayList<>();
            for (JsonElement metadataEntry : json.get("new_metadata").getAsJsonArray()) {
                metadata.add(new Pair<>(metadataEntry.getAsJsonObject().get("key").getAsString(),
                        metadataEntry.getAsJsonObject().get("value").getAsString()));
            }
            return new LibraryActionMessage(type, json.get("action_id").getAsInt(), json.get("file_name").getAsString(), metadata);
        } else {
            return new LibraryActionMessage(type, json.get("action_id").getAsInt(), json.get("file_name").getAsString());
        }
    }

    @Override
    public void handle(Client client) {
        System.out.printf("Maybe received action %d: %s\n", actionId, actionType);
        int currentActionId = Storage.getCurrentActionID();
        if(currentActionId == -1) currentActionId = 0;
        if(actionId != currentActionId) {
            // reconnect to sync the ids back up
            ErrorMessage errorMessage = new ErrorMessage(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, ErrorMessage.Action.RECONNECT);
            errorMessage.relativeToMessage = messageId;
            errorMessage.info = "Unexpected action ID! received: " + actionId + ", expected: " + currentActionId;
            errorMessage.secondsToRetry = 0;
            client.send(errorMessage);
            return;
        }
        System.out.printf("Received action %d: %s\n", actionId, actionType);
        if(actionType == Type.ADD || actionType == Type.REPLACE) {
            if (pendingAction == null || pendingAction.isCancelled() || pendingAction.finished) {
                pendingAction = new PendingAction(actionId, client.clientId, fileName, actionType);
            } else {
                ErrorMessage errorMessage = new ErrorMessage(ErrorMessage.ErrorType.BUSY, ErrorMessage.Action.RETRY);
                errorMessage.relativeToMessage = messageId;
                errorMessage.info = "Another client is trying to modify the library right now.";
                /*
                retry after timeout to see if other client fails to establish a connection, if it has already established
                a connection then wait until you get another action message which indicates the other client is done. It
                does return 60 seconds which is arbitrarily selected as an ETA for when the other client would probably
                be done sending its file, just in case it breaks the connection so that this client isn't left waiting
                eternally
                 */
                if (!pendingAction.started) {
                    errorMessage.secondsToRetry = (int) (PendingAction.CONNECTION_TIMEOUT_SECONDS + 1);
                } else {
                    errorMessage.secondsToRetry = 60;
                }
                client.send(errorMessage);
            }
        } else if(actionType == Type.CHANGE_METADATA) {
            //TODO before beta
            ErrorMessage errorMessage = new ErrorMessage(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS);
            errorMessage.relativeToMessage = messageId;
            errorMessage.info = "The server does not support changing metadata yet! :(";
            client.send(errorMessage);
        } else if(actionType == Type.REMOVE) {
            File toRemove = new File(Storage.LIBRARY, fileName);
            if(!toRemove.delete()) {
                ErrorMessage errorMessage = new ErrorMessage(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS);
                errorMessage.relativeToMessage = messageId;
                errorMessage.info = "Track " + fileName + " does not exist!";
                client.send(errorMessage);
                return;
            }
            Client.broadcastExcept(this, client.clientId);
        }
        Action action = new Action(actionId, client.clientId, fileName, actionType);
        if(actionType == Type.CHANGE_METADATA) {
            action.newMetadata = newMetadata;
        }
        Storage.actions.put(currentActionId, action);
        Storage.setCurrentActionID(currentActionId + 1);
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

    public static class Pair<T, U> {
        public T key;
        public U value;

        public Pair(T key, U value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class PendingAction extends Action {
        /**
         * The time in seconds that the client has to establish a connection to the http server to upload the file
         */
        public static final float CONNECTION_TIMEOUT_SECONDS = 10;
        /**
         * When the message relative to the action was received
         */
        public Instant creationTime;
        /**
         * If the file transfer relative to the action is completed
         */
        public boolean finished = false;
        /**
         * If the file transfer relative to the action has started
         */
        public boolean started = false;
        /**
         * If the pending action was cancelled and another can override it. Used in case a client either does not start
         * the file transfer in time or interrupts the file transfer before it is finished.<br />
         * Do not check this value directly! Use {@link #isCancelled()} instead, it will check if the client failed to
         * connect within the timeout time.
         */
        public boolean cancelled = false;

        public PendingAction(int actionId, int clientId, String fileName, Type actionType) {
            super(actionId, clientId, fileName, actionType);
            this.creationTime = Instant.now();
        }

        /**
         * Creates a blank, already cancelled pending action. Used to initialize the first value when there are no
         * pending actions yet
         */
        public PendingAction() {
            super();
            this.creationTime = Instant.MIN;
            this.cancelled = true;
        }

        /**
         * Checks if the client failed to connect within the timeout time and returns if the action is cancelled.
         */
        public boolean isCancelled() {
            if(!started && !cancelled && creationTime.plusMillis((long) (CONNECTION_TIMEOUT_SECONDS * 1000)).compareTo(Instant.now()) < 0) {
                cancelled = true;
            }
            return cancelled;
        }
    }

    //TODO maybe move??? i dont really know this might be getting a bit too big for a message class and unintuitive
    public static class Action implements Serializable {
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
        /**
         *
         */
        public Type actionType;
        public List<Pair<String, String>> newMetadata = null;

        public Action(int actionId, int clientId, String fileName, Type actionType) {
            this.actionId = actionId;
            this.clientId = clientId;
            this.fileName = fileName;
            this.actionType = actionType;
        }

        public Action(int actionId, int clientId, String fileName, Type actionType, List<Pair<String, String>> newMetadata) {
            // TODO check for correct initializer (ion feel like doing it rn)
            this.actionId = actionId;
            this.clientId = clientId;
            this.fileName = fileName;
            this.actionType = actionType;
            this.newMetadata = newMetadata;
        }

        public Action() {
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
    }

    public static void main(String[] args) {
        System.out.println("testing so hard rn");
        LibraryActionMessage message = new LibraryActionMessage(Type.CHANGE_METADATA,
                2,
                "test.flac",
                List.of(
                        new Pair("artist", "Somebody"),
                        new Pair("title", "I once had a title"),
                        new Pair("artist", "Someone else"),
                        new Pair("duration", "3 fucking years"),
                        new Pair("album", "Memory lane")
                ));
        System.out.println(message);
    }
}
