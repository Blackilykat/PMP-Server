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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

import static dev.blackilykat.Main.LOGGER;

public class Device implements Serializable {
    // all reasonably usable standard ASCII chars
    public static final String TOKEN_CHARSET = "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
    public static final int TOKEN_LENGTH = 128;

    public String token = null;
    public int id;
    public String name;
    public Instant creationTime;
    public Instant lastLogin;
    public transient Client connectedClient = null;

    /**
     * Creates a device with the given name.
     * THIS DOES NOT GENERATE A TOKEN. THE TOKEN MUST BE GENERATED MANUALLY WITH {@link #generateToken()}.
     */
    public Device(String name) {
        this(Storage.setLastDeviceId(Storage.getLastDeviceId()+1), name, Instant.now(), Instant.EPOCH);
    }

    public Device(int id, String name, Instant creationTime, Instant lastLogin) {
        this.id = id;
        this.name = name;
        this.creationTime = creationTime;
        this.lastLogin = lastLogin;
    }

    /**
     * Generates a token using {@link SecureRandom#getInstanceStrong()} and a charset defined in {@link #TOKEN_CHARSET}.<br />
     * This method kills the program by calling {@link System#exit(int)} in case there are no strong SecureRandom algorithms.<br />
     * This - as seen in the javadoc of getInstanceStrong - should never be possible as JVMs are required to have at least one such algorithm.
     */
    public static String generateToken() {
        try {
            SecureRandom random = SecureRandom.getInstanceStrong();
            char[] token = new char[TOKEN_LENGTH];
            for(int i = 0; i < TOKEN_LENGTH; i++) {
                token[i] = TOKEN_CHARSET.charAt(random.nextInt(TOKEN_CHARSET.length()));
            }
            return new String(token);
        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();

            String msg = "There are no SecureRandom algorithms supported by the JVM. This should never happen. Please report this to the developer along with the output of `java -version`.";
            LOGGER.fatal(msg, e);
            System.out.println(msg);
            System.err.println(msg);
            System.exit(1);
            // can't get to the point of returning, but it won't compile if I don't include this
            return null;
        }
    }


}
