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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.blackilykat.messages.LibraryActionMessage;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.blackilykat.Main.LOGGER;

public class FileTransferHttpHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOGGER.info("Handling {} {} request for IP {}", exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRemoteAddress().getAddress());
        String claimedToken = exchange.getRequestHeaders().get("Authorization").getFirst();
        AtomicBoolean claimedTokenMatches = new AtomicBoolean(false);
        Storage.devices.forEach((i, d) -> {
            if(d.token.equals(claimedToken)) claimedTokenMatches.set(true);
        });
        if(!claimedTokenMatches.get()) {
            LOGGER.warn("Rejecting request from {} for failing authentication", exchange.getRemoteAddress().getAddress());
            exchange.sendResponseHeaders(403, 0);
            exchange.getResponseBody().close();
            return;
        }

        String filename = URLDecoder.decode(exchange.getRequestURI().getPath().replace("..", ""), StandardCharsets.UTF_8);
        File file = new File(Storage.LIBRARY.getAbsolutePath(), filename);
        int actionId = -1;
        int clientId = -1;
        String query = exchange.getRequestURI().getQuery();
        if(query == null) {
            query = "";
        }
        String method = exchange.getRequestMethod();
        if(query.isEmpty() && (method.equals("POST") || method.equals("PUT"))) {
            LOGGER.warn("Empty query with {}", method);
            exchange.sendResponseHeaders(400, 0);
            exchange.getResponseBody().close();
            return;
        }
        for(String kv : query.split("&")) {
            String[] parts = kv.split("=");
            if(parts.length != 2) continue;
            if(parts[0].equals("action_id")) {
                try {
                    actionId = Integer.parseInt(parts[1]);
                } catch(NumberFormatException e) {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.getResponseBody().close();
                    return;
                }
            } else if(parts[0].equals("client_id")) {
                try {
                    clientId = Integer.parseInt(parts[1]);
                } catch(NumberFormatException e) {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.getResponseBody().close();
                    return;
                }
            }
        }
        // probably the scariest condition ive written in the ever
        if((method.equals("PUT") || method.equals("POST"))
                && (!filename.substring(1).equals(LibraryActionMessage.pendingAction.fileName)
                        || actionId != LibraryActionMessage.pendingAction.actionId
                        || clientId != LibraryActionMessage.pendingAction.clientId
                        || LibraryActionMessage.pendingAction.isCancelled())) {
            LOGGER.warn("Rejecting request from {} as it's not performing the pending action", exchange.getRemoteAddress().getAddress());
            exchange.sendResponseHeaders(403, 0);
            exchange.getResponseBody().close();
            return;
        }
        switch(method) {
            case "GET" -> {
                if(!file.exists()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                LOGGER.info("Accepting GET request for {}", exchange.getRemoteAddress().getAddress());
                exchange.sendResponseHeaders(200, file.length());
                OutputStream outputStream = exchange.getResponseBody();
                Files.copy(file.toPath(), outputStream);
                outputStream.close();
            }
            case "POST" -> {
                if(LibraryActionMessage.pendingAction.actionType != LibraryAction.Type.ADD) {
                    LOGGER.warn("Rejecting request from {} as it's using POST for a non-ADD pending action", exchange.getRemoteAddress().getAddress());
                    exchange.sendResponseHeaders(403, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                if(file.exists()) {
                    LOGGER.warn("Rejecting request from {} as it's adding an existing file", exchange.getRemoteAddress().getAddress());
                    LibraryActionMessage.pendingAction.cancelled = true;
                    exchange.sendResponseHeaders(400, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                LOGGER.info("Accepting POST request from {}", exchange.getRemoteAddress().getAddress());
                LibraryActionMessage.pendingAction.started = true;
                InputStream inputStream = exchange.getRequestBody();
                Files.copy(inputStream, file.toPath());
                inputStream.close();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                LibraryActionMessage.pendingAction.finished = true;
            }
            case "PUT" -> {
                if(LibraryActionMessage.pendingAction.actionType != LibraryAction.Type.REPLACE) {
                    LOGGER.warn("Rejecting request from {} as it's using PUT for a non-REPLACE pending action", exchange.getRemoteAddress().getAddress());
                    exchange.sendResponseHeaders(403, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                LOGGER.info("Accepting PUT request from {}", exchange.getRemoteAddress().getAddress());
                LibraryActionMessage.pendingAction.started = true;
                InputStream inputStream = exchange.getRequestBody();
                Files.copy(inputStream, file.toPath());
                inputStream.close();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                LibraryActionMessage.pendingAction.finished = true;
            }
            default -> {
                LOGGER.info("Rejecting with 404 to {}", exchange.getRemoteAddress().getAddress());
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        }
        if(method.equals("PUT") || method.equals("POST")) {
            Client.broadcastExcept(LibraryActionMessage.pendingAction.toMessage(), LibraryActionMessage.pendingAction.clientId);
        }
    }
}
