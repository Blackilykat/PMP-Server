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

package dev.blackilykat;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import dev.blackilykat.messages.*;
import dev.blackilykat.messages.exceptions.MessageException;
import dev.blackilykat.messages.exceptions.MessageInvalidContentsException;
import dev.blackilykat.messages.exceptions.MessageMissingContentsException;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static dev.blackilykat.Main.LOGGER;

public class Client {
    public final SSLSocket socket;
    public InputStream inputStream;
    public OutputStream outputStream;
    public boolean connected = true;
    public LoginStage loginStage = LoginStage.LOGGED_OUT;
    public final Object loginLock = new Object();
    public BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private MessageSendingThread messageSendingThread;
    private MessageReceivingThread messageReceivingThread;
    private int messageIdCounter = 0;
    public final int clientId;
    public Device device;

    public Client(SSLSocket socket, int clientId) throws IOException {
        this.clientId = clientId;
        this.socket = socket;

        messageSendingThread = new MessageSendingThread();
        messageReceivingThread = new MessageReceivingThread();

        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        socket.startHandshake();
    }

    public void startSending() {
        if(messageSendingThread.isAlive()) {
            throw new IllegalStateException("Already started");
        }
        messageSendingThread.start();
    }


    public void startReceiving() {
        if(messageReceivingThread.isAlive()) {
            throw new IllegalStateException("Already started");
        }
        messageReceivingThread.start();
    }

    public void disconnect() {
        connected = false;
        Main.clients.remove(this);
        LOGGER.info("Disconnecting client {}", this);
        // may be calling disconnect because the socket got closed
        try {
            socket.close();
        } catch (IOException ignored) {}

        try {
            messageSendingThread.interrupt();
            messageReceivingThread.interrupt();
        } catch(SecurityException e) {
            // should really never happen
            LOGGER.error("Failed to interrupt IO threads", e);
        }

        for(PlaybackSession session : PlaybackSession.getAvailableSessions()) {
            if(session.owner == this.clientId) {
                session.owner = -1;
                Instant now = Instant.now();
                session.recalculatePosition(now);
                session.playing = false;
                Client.broadcast(new PlaybackSessionUpdateMessage(session.id, null, null, null, false, session.lastPositionUpdate, -1, null, null, null, session.lastPositionUpdateTime));
            }
        }
    }

    public void send(Message message) {
        messageQueue.add(message);
    }

    public void sendError(ErrorMessage.ErrorType type, int messageId, String info) {
        ErrorMessage errorMessage = new ErrorMessage(type);
        if(info != null) errorMessage.info = info;
        if(messageId >= 0) errorMessage.relativeToMessage = messageId;
        send(errorMessage);
    }

    public static void broadcast(Message message) {
        for (Client client : Main.clients) {
            client.send(message);
        }
    }

    public static void broadcastExcept(Message message, int clientId) {
        for(Client client : Main.clients) {
            if(client.clientId == clientId) continue;
            client.send(message);
        }
    }

    @Override
    public String toString() {
        return String.format("Client%d(%s)", clientId, socket.getInetAddress().toString());
    }

    /**
     * Increases {@link #messageIdCounter}. It's a method because two threads could try to do this at the same time.
     */
    public synchronized void increaseMessageIdCounter() {
        messageIdCounter++;
    }

    public int getMessageIdCounter() {
        return messageIdCounter;
    }

    private class MessageSendingThread extends Thread {

        public MessageSendingThread() {
            super("Message sending thread for client " + clientId);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Message message = messageQueue.take();
                    if(message instanceof ErrorMessage err) {
                        LOGGER.warn("""
                                Sending error to client {}:
                                  - type            : {}
                                  - action          : {}
                                  - relative to     : {}
                                  - seconds to retry: {}
                                  - info            : {}""",
                                Client.this.clientId,
                                err.errorType,
                                err.action,
                                err.relativeToMessage,
                                err.secondsToRetry,
                                err.info);
                    }
                    String messageStr = (message.withMessageId(getMessageIdCounter()).toJson() + "\n");
                    String printedMessage = messageStr;

                    if(message instanceof WelcomeMessage wel) {
                        String oldToken = wel.token;
                        wel.token = "REDACTED";
                        printedMessage = (message.withMessageId(getMessageIdCounter()).toJson() + "\n");
                        wel.token = oldToken;
                    }

                    LOGGER.info("Sending message to client {}: {}", clientId, printedMessage.trim());
                    outputStream.write(messageStr.getBytes(StandardCharsets.UTF_8));
                    increaseMessageIdCounter();
                }
            } catch (IOException e) {
                LOGGER.error("IO exception", e);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted", e);
            } catch (Exception e) {
                LOGGER.error("Unknown exception", e);
            } finally {
                disconnect();
            }
        }
    }

    private class MessageReceivingThread extends Thread {

        public MessageReceivingThread() {
            super("Message receiving thread for client " + clientId);
        }

        @Override
        public void run() {
            Queue<Byte> inputBuffer = new ArrayDeque<>();
            try {
                int read;
                while(!Thread.interrupted()) {
                    read = inputStream.read();
                    if(read == -1) break;
                    if(read != ((int) '\n')) {
                        inputBuffer.add((byte) read);
                    } else if(!inputBuffer.isEmpty()) {
                        byte[] msg = new byte[inputBuffer.size()];
                        for(int i = 0; i < msg.length; i++) {
                            msg[i] = inputBuffer.poll();
                        }
                        String message = new String(msg, StandardCharsets.UTF_8);
                        // avoid printing password. Doesn't need to be a flawless check as these are debug prints.
                        if(!message.contains("\"LOGIN\"")) {
                            LOGGER.info("Received from client {}: {}", clientId, message);
                        }
                        try {
                            JsonObject json = Json.fromJsonObject(message);
                            String messageType;
                            if(json.has("message_type")) {
                                messageType = json.get("message_type").getAsString();
                            } else {
                                throw new MessageMissingContentsException("Missing message_type field!");
                            }

                            while(loginStage == LoginStage.PROCESSING) {
                                synchronized(loginLock) {
                                    loginLock.wait();
                                }
                            }
                            if(loginStage == LoginStage.LOGGED_OUT && !messageType.equals(LoginMessage.MESSAGE_TYPE)) {
                                increaseMessageIdCounter();
                                sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, getMessageIdCounter()-1, "Logged out");
                                continue;
                            }

                            if(!json.has("message_id")) {
                                increaseMessageIdCounter();
                                sendError(ErrorMessage.ErrorType.MESSAGE_MISSING_CONTENTS, -1, "Missing message id!");
                            }
                            int messageId = json.get("message_id").getAsInt();

                            Message parsedMessage = switch(messageType.toUpperCase()) {
                                case WelcomeMessage.MESSAGE_TYPE -> WelcomeMessage.fromJson(json);
                                case DisconnectMessage.MESSAGE_TYPE -> DisconnectMessage.fromJson(json);
                                case ErrorMessage.MESSAGE_TYPE -> ErrorMessage.fromJson(json);
                                case LibraryActionMessage.MESSAGE_TYPE -> LibraryActionMessage.fromJson(json);
                                case TestMessage.MESSAGE_TYPE -> TestMessage.fromJson(json);
                                case LibraryActionRequestMessage.MESSAGE_TYPE -> LibraryActionRequestMessage.fromJson(json);
                                case PlaybackSessionCreateMessage.MESSAGE_TYPE -> PlaybackSessionCreateMessage.fromJson(json);
                                case PlaybackSessionUpdateMessage.MESSAGE_TYPE -> PlaybackSessionUpdateMessage.fromJson(json);
                                case PlaybackSessionListMessage.MESSAGE_TYPE -> PlaybackSessionListMessage.fromJson(json);
                                case DataHeaderListMessage.MESSAGE_TYPE -> DataHeaderListMessage.fromJson(json);
                                case LoginMessage.MESSAGE_TYPE -> LoginMessage.fromJson(json);
                                case LatestHeaderIdMessage.MESSAGE_TYPE -> LatestHeaderIdMessage.fromJson(json);
                                default -> {
                                    throw new MessageInvalidContentsException("Unknown message_type '"+messageType+"'");
                                }
                            };
                            parsedMessage.messageId = messageId;
                            parsedMessage.handle(Client.this);

                            LOGGER.info("Received message w/ type {}", parsedMessage.getMessageType());

                            increaseMessageIdCounter();
                        } catch (JsonSyntaxException | UnsupportedOperationException e) {
                            increaseMessageIdCounter();
                            sendError(ErrorMessage.ErrorType.MESSAGE_FORMAT, getMessageIdCounter()-1, e.getMessage());
                        } catch (MessageInvalidContentsException e) {
                            increaseMessageIdCounter();
                            sendError(ErrorMessage.ErrorType.MESSAGE_INVALID_CONTENTS, getMessageIdCounter()-1, e.getMessage());
                        } catch (MessageMissingContentsException e) {
                            increaseMessageIdCounter();
                            sendError(ErrorMessage.ErrorType.MESSAGE_MISSING_CONTENTS, getMessageIdCounter()-1, e.getMessage());
                        } catch (MessageException ignored) {
                            //unreachable
                        } catch(InterruptedException e) {
                            // The fact we got this exception implies that Thread.interrupted() was called somewhere
                            // along the way, which clears the interrupted status of the thread. To ensure the loop
                            // above can tell the thread was interrupted, we must interrupt it again.
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("IO exception", e);
            } catch (Exception e) {
                LOGGER.error("Unknown exception", e);
            } finally {
                disconnect();
            }
        }
    }
}
