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

import org.h2.mvstore.MVStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class Storage {
    public static final File LIBRARY = new File("library/");
    public static Map<Integer, LibraryAction> actions;
    public static Map<String, Object> general;

    public static void init() {
        if(!LIBRARY.exists()) {
            LIBRARY.mkdirs();
        } else if(!LIBRARY.isDirectory()) {
            throw new RuntimeException("library is a file! It must be renamed or deleted for the program to function.");
        }


        MVStore mvStore = MVStore.open("db");
        actions = mvStore.openMap("actions");
        general = mvStore.openMap("general");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            setSessionList(PlaybackSession.packUpSessions());
            setSessionIDCounter(PlaybackSession.idCounter);
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

    // MVStore cries if I try putting an arraylist into it (even though its serializable) so it has to be an array
    public static List<PlaybackSession> getSessionList() {
        PlaybackSession[] array = (PlaybackSession[]) general.getOrDefault("sessionList", new PlaybackSession[0]);
        return new ArrayList<>(List.of(array));
    }

    public static void setSessionList(List<PlaybackSession> newValue) {
        general.put("sessionList", newValue.toArray(new PlaybackSession[0]));
    }

    public static int getSessionIDCounter() {
        return (Integer) general.getOrDefault("sessionIDCounter", 1);
    }

    public static void setSessionIDCounter(int newValue) {
        general.put("sessionIDCounter", newValue);
    }

    public static List<Pair<String, String>> getTrackDataHeaders() {
        Pair<String, String>[] array = (Pair<String, String>[]) general.getOrDefault("trackDataHeaders", new Pair[0]);
        return new ArrayList<>(List.of(array));
    }

    public static void setTrackDataHeaders(List<Pair<String, String>> newValue) {
        general.put("trackDataHeaders", newValue.toArray(new Pair[0]));
    }
}
