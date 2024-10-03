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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;

// TODO this is bad pls fix
public class Json {
    // if i have to change its properties and stuff i can
    public static final Gson GSON = new Gson();

    /**
     * Converts an object to a json string
     * @see Gson#toJson(Object)
     */
    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Converts the json string to a JsonObject
     * @see Gson#fromJson(String, Type)
     */
    public static JsonObject fromJsonObject(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }

    /**
     * Converts the json string to a JsonElement
     * @see Gson#fromJson(String, Type)
     */
    public static JsonElement fromJsonElement(String json) {
        return GSON.fromJson(json, JsonElement.class);
    }
}
