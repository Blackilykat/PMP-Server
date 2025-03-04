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

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PlaybackSession implements Serializable {
    private static List<PlaybackSession> availableSessions = Storage.getSessionList();
    public static int idCounter = Storage.getSessionIDCounter();
    public int id;
    public String track = null;
    public ShuffleOption shuffle = ShuffleOption.OFF;
    public RepeatOption repeat = RepeatOption.OFF;
    public boolean playing = false;
    public int lastPositionUpdate = 0;
    public Instant lastPositionUpdateTime = null;
    public int owner = -1;

    public PlaybackSession(int id) {
        this.id = id;
    }

    public void register() {
        availableSessions.add(this);
    }

    public static PlaybackSession[] getAvailableSessions() {
        return availableSessions.toArray(new PlaybackSession[0]);
    }

    public void recalculatePosition(Instant atTime) {
        int offset = 0;
        if(playing && lastPositionUpdateTime != null) {
            offset = (int) (((atTime.toEpochMilli() - lastPositionUpdateTime.toEpochMilli()) * 44100 * 4) / 1000);
        }
        offset -= offset % 4;
        lastPositionUpdate += offset;
        lastPositionUpdateTime = atTime;
    }

    /**
     * Prepares sessions to be stored at shutdown. Must not be called in any other occasion as it removes every
     * session's owner and pauses it without sending any update to connected clients.
     */
    public static List<PlaybackSession> packUpSessions() {
        Instant now = Instant.now();
        for(PlaybackSession session : availableSessions) {
            Instant lastUpdate = session.lastPositionUpdateTime;
            session.recalculatePosition(now);
            // make sure clients have a more recent update so they can inform the server of the new position once it's back up
            session.lastPositionUpdateTime = lastUpdate.minusMillis(1000);
            session.playing = false;
            session.owner = -1;
        }
        return availableSessions;
    }

    public enum ShuffleOption {
        ON,
        OFF
    }

    public enum RepeatOption {
        /**
         * Repeat this track. Takes priority over shuffle.
         */
        TRACK,
        /**
         * When at the end of the track list, get back to the start. Does nothing if shuffle is on.
         */
        ALL,
        /**
         * When at the end of the track list, stop playing.
         */
        OFF
    }
}
