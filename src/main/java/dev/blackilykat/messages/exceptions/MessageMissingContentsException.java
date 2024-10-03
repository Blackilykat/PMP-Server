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

package dev.blackilykat.messages.exceptions;

public class MessageMissingContentsException extends MessageException {
    public MessageMissingContentsException() {
        super();
    }

    public MessageMissingContentsException(String message) {
        super(message);
    }

    public MessageMissingContentsException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageMissingContentsException(Throwable cause) {
        super(cause);
    }
}
