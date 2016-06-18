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
package net.andylizi.webinterface.http;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HttpParams {
    private Map<String, String> params;
    
    private ByteBuf content;
    private Charset charset;
    private Map<String, String> apply;

    public HttpParams(Map<String, String> params) {
        this.params = Objects.requireNonNull(params);
    }
    
    public HttpParams(ByteBuf content, Charset charset, Map<String, String> apply) {
        this.content = Objects.requireNonNull(content);
        this.charset = charset == null ? CharsetUtil.UTF_8 : charset;
        this.apply = apply == null ? new HashMap<String, String>(0) : apply;
    }
    
    public Map<String, String> params(){
        if(params == null)
            params = parseParams(content.toString(charset), apply);
        return params;
    }
    
    public static Map<String, String> parseParams(String params, Map<String, String> apply){
        Map<String, String> result = new HashMap<>(apply);
        for(String param : params.split("&")){
            String[] entry = param.split("=", 2);
            if(entry.length > 1)
                result.put(entry[0], entry[1]);
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public String toString() {
        return params().toString();
    }

    @Override
    public boolean equals(Object obj) {
        return params().equals(obj);
    }

    @Override
    public int hashCode() {
        return params().hashCode();
    }
}
