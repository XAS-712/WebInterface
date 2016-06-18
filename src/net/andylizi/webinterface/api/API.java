/*
 * Copyright (C) 2016 andylizi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.andylizi.webinterface.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;

import net.andylizi.webinterface.http.HttpModule;
import net.andylizi.webinterface.websocket.WebSocketModule;

public abstract class API {
    static final Map<String, HttpModule> httpRegistry = new HashMap<>();
    static final Map<String, WebSocketModule> websocketRegistry = new HashMap<>();

    public static void registerModule(String id, Module module) throws NullPointerException, IllegalArgumentException{
        id = Objects.requireNonNull(id).toLowerCase();
        boolean success = false;
        if(Objects.requireNonNull(module) instanceof HttpModule){
            if(httpRegistry.containsKey(id))
                throw new IllegalArgumentException("Module Id already exists");
            httpRegistry.put(id, (HttpModule) module);
            success = true;
        }
        if(module instanceof WebSocketModule){
            if(websocketRegistry.containsKey(id))
                throw new IllegalArgumentException("Module Id already exists");
            websocketRegistry.put(id, (WebSocketModule) module);
            success = true;
        }
        if(!success)
            throw new IllegalArgumentException("Invalid module type");
    }
    
    public static boolean unregisterModule(String id) throws NullPointerException{
        id = Objects.requireNonNull(id).toLowerCase();
        if(httpRegistry.remove(id) == null)
            return websocketRegistry.remove(id) != null;
        else
            return true;
    }
    
    public static Module lookupModule(String id) {
        if(id == null)
            return null;
        id = id.toLowerCase();
        StringTokenizer tokenizer = new StringTokenizer(id, "?");
        if(tokenizer.hasMoreTokens())
            id = tokenizer.nextToken();
        Module module;
        return (module = httpRegistry.get(id)) == null ? websocketRegistry.get(id) : module;
    }

    private API() throws AssertionError{ throw new AssertionError(); }
}
