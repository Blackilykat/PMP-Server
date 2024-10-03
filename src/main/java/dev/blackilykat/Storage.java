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
import org.h2.mvstore.MVStore;

import java.io.File;
import java.util.Map;

public class Storage {
    public static final File LIBRARY = new File("library/");
    // using the message's class cause it has all the needed info
    public static Map<Integer, LibraryActionMessage.Action> actions;
    public static Map<String, Object> general;

    public static void init() {
        MVStore mvStore = MVStore.open("db");
        actions = mvStore.openMap("actions");
        general = mvStore.openMap("general");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            mvStore.close();
        }));
    }

    public static int getCurrentActionID() {
        /*
        default is -1 for clients who need to know when they should just get the entire library without caring about
        library actions, but the server will always have the entire library and all actions so if there are no actions
        the first will always be ID 0.
         */
        return (Integer) general.getOrDefault("currentActionID", 0);
    }

    public static void setCurrentActionID(int newValue) {
        general.put("currentActionID", newValue);
    }
}
