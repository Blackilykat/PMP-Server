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
import org.h2.mvstore.MVStoreException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.blackilykat.Main.LOGGER;

@SuppressWarnings("unchecked")
public class Storage {
    public static final Object reopeningStoreLock = new Object();


    public static final File LIBRARY = new File("library/");
    public static Map<Integer, LibraryAction> actions;
    public static Map<Integer, Device> devices;
    public static Map<String, Object> general;
    public static Map<String, Track> cachedTracks;
    private static Map<Boolean, Boolean> openChecker;
    public static MVStore mvStore;

    private static void openStore() {
        mvStore = MVStore.open("db");
        actions = mvStore.openMap("actions");
        devices = mvStore.openMap("devices");
        general = mvStore.openMap("general");
        openChecker = mvStore.openMap("openChecker");
        cachedTracks = mvStore.openMap("cachedTracks");
        PlaybackSession.idCounter = getSessionIDCounter();
        PlaybackSession.availableSessions = getSessionList();
    }

    /**
     * Performs a read and a write operation to the store to check whether it's been unexpectedly closed.<br/>
     * Calling {@link MVStore#isClosed()} returns an incorrect value if the underlying file was closed by an interrupt.
     */
    public static boolean isStoreClosed() {
        synchronized(reopeningStoreLock) {
            try {
                openChecker.put(false, !openChecker.getOrDefault(false, false));
                return false;
            } catch(MVStoreException e) {
                return true;
            }
        }
    }

    /**
     * Thread-safe method to reopen the MVStore if it is closed.
     * @return whether the store was reopened or not
     */
    public static boolean maybeReopenStore() {
        synchronized(reopeningStoreLock) {
            if(isStoreClosed()) {
                openStore();
                LOGGER.info("Reopened store");
                return true;
            }
            LOGGER.info("Store was already open");
            return false;
        }
    }

    public static void init(final boolean saveOnShutdown) {
        if(!LIBRARY.exists()) {
            LIBRARY.mkdirs();
        } else if(!LIBRARY.isDirectory()) {
            throw new RuntimeException("library is a file! It must be renamed or deleted for the program to function.");
        }

        openStore();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (saveOnShutdown) {
                setSessionList(PlaybackSession.packUpSessions());
                setSessionIDCounter(PlaybackSession.idCounter);
            }
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

    public static List<Triple<Integer, String, String>> getTrackDataHeaders() {
        Triple<Integer, String, String>[] array = (Triple<Integer, String, String>[]) general.getOrDefault("trackDataHeaders", new Triple[0]);
        return new ArrayList<>(List.of(array));
    }

    public static void setTrackDataHeaders(List<Triple<Integer, String, String>> newValue) {
        general.put("trackDataHeaders", newValue.toArray(new Triple[0]));
    }

    public static String getPassword() {
        if(!general.containsKey("password")) return null;
        return general.get("password").toString();
    }

    public static int getLatestHeaderId() {
        return (int) general.getOrDefault("latestHeaderId", 1);
    }

    public static void setLatestHeaderId(int id) {
        if(id < 1) {
            general.remove("latestHeaderId");
            return;
        }
        general.put("latestHeaderId", id);
    }

    public static int getLastDeviceId() {
        return (int) general.getOrDefault("lastDeviceId", 1);
    }

    public static int setLastDeviceId(int id) {
        if(id < 1) {
            general.remove("lastDeviceId");
            return id;
        }
        general.put("lastDeviceId", id);
        return id;
    }
}
