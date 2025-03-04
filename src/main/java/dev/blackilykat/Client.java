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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
    public final SSLSocket socket;
    public InputStream inputStream;
    public OutputStream outputStream;
    public boolean connected = true;
    public BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    public StringBuffer inputBuffer = new StringBuffer();
    private MessageSendingThread messageSendingThread = new MessageSendingThread();
    private MessageReceivingThread messageReceivingThread = new MessageReceivingThread();
    private int messageIdCounter = 0;
    public final int clientId;

    public Client(SSLSocket socket, int clientId) throws IOException {
        this.clientId = clientId;
        this.socket = socket;
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        Main.clients.add(this);
        socket.startHandshake();
    }

    public void start() {
        messageSendingThread.start();
        messageReceivingThread.start();
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
        System.out.println("Disconnecting client " + this);
        // may be calling disconnect because the socket got closed
        try {
            socket.close();
        } catch (IOException ignored) {}

        for(PlaybackSession session : PlaybackSession.getAvailableSessions()) {
            if(session.owner == this.clientId) {
                session.owner = -1;
                Instant now = Instant.now();
                session.recalculatePosition(now);
                session.playing = false;
                Client.broadcast(new PlaybackSessionUpdateMessage(session.id, null, null, null, false, session.lastPositionUpdate, -1, session.lastPositionUpdateTime));
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
        @Override
        public void run() {
            try {
                while (true) {
                    Message message = messageQueue.take();
                    if(message instanceof ErrorMessage err) {
                        System.err.printf("""
                                Sending error to client %d:
                                  - type            : %s
                                  - action          : %s
                                  - relative to     : %d
                                  - seconds to retry: %d
                                  - info            : %s
                                """,
                                Client.this.clientId,
                                err.errorType,
                                err.action,
                                err.relativeToMessage,
                                err.secondsToRetry,
                                err.info);
                    }
                    String messageStr = (message.withMessageId(getMessageIdCounter()).toJson() + "\n");
                    System.out.printf("Sending message to client %d: %s\n", clientId, messageStr);
                    outputStream.write(messageStr.getBytes(StandardCharsets.UTF_8));
                    increaseMessageIdCounter();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException ignored) {
            } finally {
                disconnect();
            }
        }
    }

    private class MessageReceivingThread extends Thread {
        @Override
        public void run() {
            try {
                int read;
                while(!Thread.interrupted()) {
                    read = inputStream.read();
                    if(read == -1) break;
                    if(read != ((int) '\n')) {
                        inputBuffer.append((char) read);
                    } else if(!inputBuffer.isEmpty()) {
                        String message = inputBuffer.toString();
                        System.out.printf("Received from client %d: %s\n", clientId, message);
                        try {
                            JsonObject json = Json.fromJsonObject(message);
                            String messageType;
                            if(json.has("message_type")) {
                                messageType = json.get("message_type").getAsString();
                            } else {
                                throw new MessageMissingContentsException("Missing message_type field!");
                            }

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
                                default -> {
                                    throw new MessageInvalidContentsException("Unknown message_type '"+messageType+"'");
                                }
                            };
                            parsedMessage.handle(Client.this);

                            System.out.println("Received message w/ type " + parsedMessage.getMessageType());

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
                        }


                        inputBuffer.setLength(0);
                    }
                }
            } catch (IOException e) {

                throw new RuntimeException(e);
            } finally {
                disconnect();
            }
        }
    }
}
