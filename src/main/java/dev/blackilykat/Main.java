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

import com.sun.net.httpserver.HttpServer;
import dev.blackilykat.messages.LibraryHashesMessage;
import dev.blackilykat.messages.TestMessage;
import dev.blackilykat.messages.WelcomeMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;

public class Main {
    public static ArrayList<Client> clients = new ArrayList<>();
    public static int clientIdCounter = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("Initializing database...");
        Storage.init();
        System.out.println("Initialized database");
        System.out.println("Starting file transfer server...");
        HttpServer fileTransferHttpServer = HttpServer.create(new InetSocketAddress(5001), 0);
        fileTransferHttpServer.createContext("/", new FileTransferHttpHandler());
        fileTransferHttpServer.start();
        System.out.println("Started file transfer server");

        System.out.println("Starting main server");
        ServerSocket serverSocket = new ServerSocket(5000);

        while(true) {
            Client client = new Client(serverSocket.accept(), clientIdCounter++);
            client.start();
            client.send(new WelcomeMessage(client.clientId, Storage.getCurrentActionID()));
            System.out.println("Connected to client " + client);
            System.out.println("All connected clients: " + clients.toString());
            Client.broadcast(new TestMessage(client.clientId));
            client.send(LibraryHashesMessage.create());
        }
    }
}