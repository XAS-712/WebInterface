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
package net.andylizi.webinterface;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import net.andylizi.webinterface.api.API;
import net.andylizi.webinterface.api.Module;
import net.andylizi.webinterface.api.events.ModuleRequestEvent;
import net.andylizi.webinterface.http.HttpModule;
import net.andylizi.webinterface.http.HttpParams;
import net.andylizi.webinterface.websocket.WebSocketConnection;
import net.andylizi.webinterface.websocket.WebSocketModule;
import org.bukkit.Bukkit;

public class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
    private final Main plugin;
    private final String SERVER;
    private final String X_POWERED_BY;

    public ServerHandler(Main plugin) {
        this.plugin = plugin;
        this.SERVER = plugin.getDescription().getName();
        this.X_POWERED_BY = SERVER+'/'+plugin.getDescription().getVersion();
    }

    private static final Pattern MODULE_NAME_PATTERN = Pattern.compile("(?!/)(?<module>[^/\\?]*)");
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if(!request.getDecoderResult().isSuccess()){
            sendError(ctx, BAD_REQUEST);
            return;
        }

        Matcher matcher = MODULE_NAME_PATTERN.matcher(request.getUri());
        String moduleId = matcher.find() ? moduleId = matcher.group() : null;
        
        {
            ModuleRequestEvent event = new ModuleRequestEvent(moduleId, ctx.channel().remoteAddress());
            Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()){
                sendCustomError(ctx, FORBIDDEN, "The request has been canceled by an event listener");
                return;
            }
            moduleId = event.getModuleId();
        }
        
        Module module = API.lookupModule(moduleId);
        if(module == null){
            sendError(ctx, NOT_FOUND);
            return;
        }
        String uri = request.getUri().substring(matcher.end());
        if(uri.isEmpty() || uri.charAt(0) != '/')
            uri = '/'+uri;

        Map<String, String> params = null;
        String[] temp = request.getUri().split("\\?", 2);
        if(temp.length < 2)
            params = Collections.EMPTY_MAP;
        else
            params = HttpParams.parseParams(URLDecoder.decode(temp[1], "UTF-8"), new HashMap<String, String>());
        
        if(module instanceof HttpModule && !request.headers().contains("Upgrade")){
            FullHttpResponse response;
            try{
                response = ((HttpModule) module).handleRequest(uri, new HttpParams(request.content(), CharsetUtil.UTF_8, params), request);
            }catch(Exception ex){
                sendError(ctx, INTERNAL_SERVER_ERROR);
                ex.printStackTrace();
                return;
            }
            if(response == null){
                ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, NO_CONTENT)).addListener(ChannelFutureListener.CLOSE);
                return;
            }

            if(!response.setProtocolVersion(HTTP_1_1)
                    .headers()
                        .set("Server", SERVER)
                        .set("X-Powered-By", X_POWERED_BY)
                        .set("Module", moduleId)
                    .contains("Content-Encoding"))
                for(String str : request.headers().getAll("Accept-Encoding"))
                    if(str.toLowerCase().contains("gzip"))
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            try (GZIPOutputStream out = new GZIPOutputStream(baos)) {
                                out.write(response.content().array());
                            }
                            response.content().clear().writeBytes(baos.toByteArray());
                            response.headers()
                                    .set("Content-Encoding", "gzip")
                                    .add("Very", "Accept-Encoding");
                        }
            if(!response.headers().contains("Date"))
                response.headers().add("Date", new Date());
            if(plugin.accessControlAllowOrigin != null &&
                    !response.headers().contains("Access-Control-Allow-Origin"))
                response.headers().add("Access-Control-Allow-Origin", plugin.accessControlAllowOrigin);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }else if(module instanceof WebSocketModule){
            WebSocketServerHandshaker handshaker = new WebSocketServerHandshakerFactory(
                    getWebSocketLocation(ctx.pipeline(), request), null, false).newHandshaker(request);
            if(handshaker == null)
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            else{
                handshaker.handshake(ctx.channel(), request);
                WebSocketConnection conn = ((WebSocketModule) module)
                        .newConnect(ctx, handshaker, uri, new HttpParams(request.content(), CharsetUtil.UTF_8, params), request);
                try{
                    conn.register();
                }catch(Exception ex){
                    conn.handleException(ctx, ex);
                }
            }
        }else{
            sendError(ctx, NOT_FOUND);
            return;
        }
    }
    
    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status){
        sendCustomError(ctx, status, null);
    }
    
    private static void sendCustomError(ChannelHandlerContext ctx, HttpResponseStatus status, String msg){
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, 
                status, 
                Unpooled.copiedBuffer(status.toString().concat(msg == null ? "" : "\r\n".concat(msg)), CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=utf-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    private static String getWebSocketLocation(ChannelPipeline pipeline, HttpRequest request){
        String protocol = "ws";
        if (pipeline.get(SslHandler.class) != null)
          protocol = "wss";
        return protocol + "://" + request.headers().get("Host") + request.getUri();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex) throws Exception {
        ex.printStackTrace();
        ctx.close();
    }
}
