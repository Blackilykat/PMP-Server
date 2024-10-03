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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.blackilykat.messages.LibraryActionMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

//TODO authentication when i get around to it with the other socket as well
public class FileTransferHttpHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String filename = exchange.getRequestURI().getPath().replace("..", "");
        File file = new File(Storage.LIBRARY.getAbsolutePath(), filename);
        int actionId = -1;
        int clientId = -1;
        String query = exchange.getRequestURI().getQuery();
        if(query == null) {
            query = "";
        }
        String method = exchange.getRequestMethod();
        if(query.isEmpty() && (method.equals("POST") || method.equals("PUT"))) {
            System.out.println(method);
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
            // not sure if this is the appropriate
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
                exchange.sendResponseHeaders(200, file.length());
                OutputStream outputStream = exchange.getResponseBody();
                Files.copy(file.toPath(), outputStream);
                outputStream.close();
            }
            case "POST" -> {
                if(LibraryActionMessage.pendingAction.actionType != LibraryActionMessage.Type.ADD) {
                    exchange.sendResponseHeaders(403, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                if(file.exists()) {
                    LibraryActionMessage.pendingAction.cancelled = true;
                    exchange.sendResponseHeaders(400, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                LibraryActionMessage.pendingAction.started = true;
                InputStream inputStream = exchange.getRequestBody();
                Files.copy(inputStream, file.toPath());
                inputStream.close();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                LibraryActionMessage.pendingAction.finished = true;
            }
            case "PUT" -> {
                if(LibraryActionMessage.pendingAction.actionType != LibraryActionMessage.Type.REPLACE) {
                    exchange.sendResponseHeaders(403, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                LibraryActionMessage.pendingAction.started = true;
                InputStream inputStream = exchange.getRequestBody();
                Files.copy(inputStream, file.toPath());
                inputStream.close();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                LibraryActionMessage.pendingAction.finished = true;
            }
            default -> {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        }
        if(method.equals("PUT") || method.equals("POST")) {
            Client.broadcastExcept(LibraryActionMessage.pendingAction.toMessage(), LibraryActionMessage.pendingAction.clientId);
        }
    }
}
