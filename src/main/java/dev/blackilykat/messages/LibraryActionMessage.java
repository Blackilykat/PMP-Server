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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.blackilykat.Client;
import dev.blackilykat.Json;
import dev.blackilykat.LibraryAction;
import dev.blackilykat.Pair;
import dev.blackilykat.Storage;
import dev.blackilykat.messages.exceptions.MessageException;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static dev.blackilykat.Main.LOGGER;

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
    public LibraryAction.Type actionType;
    public String fileName;
    public List<Pair<String, String>> newMetadata;

    public LibraryActionMessage(LibraryAction.Type type, int actionId, String fileName) {
        if(type == LibraryAction.Type.CHANGE_METADATA) {
            throw new IllegalArgumentException("Incorrect initializer: expected List<Pair<String, String>> as fourth argument for action type " + type);
        }
        this.actionType = type;
        this.actionId = actionId;
        this.fileName = fileName;
    }

    public LibraryActionMessage(LibraryAction.Type type, int actionId, String fileName, List<Pair<String, String>> newMetadata) {
        if(type != LibraryAction.Type.CHANGE_METADATA) {
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
        if(actionType == LibraryAction.Type.CHANGE_METADATA) {
            object.add("new_metadata", Json.GSON.toJsonTree(newMetadata));
        }
    }

    //@Override
    public static LibraryActionMessage fromJson(JsonObject json) throws MessageException {
        LibraryAction.Type type = LibraryAction.Type.valueOf(json.get("action_type").getAsString());
        if(type == LibraryAction.Type.CHANGE_METADATA) {
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
        LOGGER.info("Maybe received action {}: {}", actionId, actionType);
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
        LOGGER.info("Received action {}: {}", actionId, actionType);
        if(actionType == LibraryAction.Type.ADD || actionType == LibraryAction.Type.REPLACE) {
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
        } else if(actionType == LibraryAction.Type.CHANGE_METADATA) {
            //TODO before beta
            ErrorMessage errorMessage = new ErrorMessage(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS);
            errorMessage.relativeToMessage = messageId;
            errorMessage.info = "The server does not support changing metadata yet! :(";
            client.send(errorMessage);
            return;
        } else if(actionType == LibraryAction.Type.REMOVE) {
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
        LibraryAction action = new LibraryAction(actionId, client.clientId, fileName, actionType);
        if(actionType == LibraryAction.Type.CHANGE_METADATA) {
            action.newMetadata = newMetadata;
        }
        Storage.actions.put(currentActionId, action);
        Storage.setCurrentActionID(currentActionId + 1);
    }



    public static class PendingAction extends LibraryAction {
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

        public PendingAction(int actionId, int clientId, String fileName, LibraryAction.Type actionType) {
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
}
