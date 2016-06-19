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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import io.netty.handler.codec.http.HttpVersion;
import static io.netty.util.CharsetUtil.UTF_8;

public class Utils {
    public static final Utils INSTANCE = new Utils();
    
    public static final String MIME_TEXT = "text/plain";
    public static final String MIME_HTML = "text/html";
    public static final String MIME_JSON = "application/json";

    public FullHttpResponse sendError(HttpResponseStatus status, String msg, ChannelHandlerContext ctx){
        FullHttpResponse response = builder(status)
                .content(status.toString().concat(msg == null ? "" : "\r\n".concat(msg)))
                .contentTypePlainText()
                .build();
        if(ctx != null)
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        return response;
    }
    
    public FullHttpResponse sendError(HttpResponseStatus status, String msg){
        return sendError(status, msg, null);
    }
    
    public FullHttpResponse sendError(HttpResponseStatus status, ChannelHandlerContext ctx){
        return sendError(status, null, ctx);
    }
    
    public FullHttpResponse sendError(HttpResponseStatus status){
        return sendError(status, null, null);
    }
    
    public FullHttpResponse sendRedirect(String url, ChannelHandlerContext ctx, boolean permanently){
        FullHttpResponse response = builder(permanently ? MOVED_PERMANENTLY : FOUND)
                .header("Location", url)
                .build();
        if(ctx != null)
            ctx.writeAndFlush(response);
        return response;
    }
    
    public FullHttpResponse sendRedirect(String url, ChannelHandlerContext ctx){
        return sendRedirect(url, ctx, false);
    }
    
    public FullHttpResponse sendRedirect(String url, boolean permanently){
        return sendRedirect(url, null, permanently);
    }
    
    public FullHttpResponse sendRedirect(String url){
        return sendRedirect(url, null);
    }

    public ResponseBuilder builder(HttpResponseStatus status){
        return new ResponseBuilder(status);
    }
    
    public ResponseBuilder builder(){
        return builder(HttpResponseStatus.OK);
    }
    
    public static class ResponseBuilder{
        private FullHttpResponse response;

        public ResponseBuilder(HttpResponseStatus status) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        }

        public ResponseBuilder(FullHttpResponse response) {
            this.response = response;
        }
        
        public ResponseBuilder content(String str){
            byte[] data = str.getBytes(UTF_8);
            this.response.content().clear().capacity(data.length).writeBytes(data);
            return header("Content-Length", data.length);
        }
        
        public ResponseBuilder content(ByteBuf data){
            this.response.content().clear().capacity(data.readableBytes()).writeBytes(data);
            return header("Content-Length", data.readableBytes());
        }

        public ResponseBuilder header(String header, Object value){
            response.headers().add(header, value);
            return this;
        }
        
        public ResponseBuilder contentType(String mime, boolean withUTF8){
            if(withUTF8)
                return header("Content-Type", mime.concat("; charset=utf-8"));
            else
                return header("Content-Type", mime);
        }
        
        public ResponseBuilder contentTypeJSON(){
            return contentType(MIME_JSON, true);
        }
        
        public ResponseBuilder contentTypePlainText(){
            return contentType(MIME_TEXT, true);
        }
        
        public ResponseBuilder contentTypeHTML(){
            return contentType(MIME_HTML, true);
        }
        
        public FullHttpResponse build(){
            return response;
        }
    }
}
