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
package net.andylizi.webinterface.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.websocketx.*;

import java.nio.ByteBuffer;
import java.util.Objects;
import net.andylizi.webinterface.api.Utils;

public abstract class WebSocketConnection extends Utils{
    private final Internal internal;
    private final WebSocketServerHandshaker handshaker;
    private final ChannelHandlerContext context;

    public WebSocketConnection(WebSocketServerHandshaker handshaker, ChannelHandlerContext context) throws NullPointerException{
        this.handshaker = Objects.requireNonNull(handshaker);
        this.internal = new Internal();
        this.context = Objects.requireNonNull(context);
        context.channel().pipeline().removeLast();
        context.channel().pipeline().addLast("internal-handler", internal);
    }
    
    public void register() throws Exception{};

    public void handleDisconnect(ChannelHandlerContext context) throws Exception{}
    
    public void handleTextMessage(ChannelHandlerContext context, String msg) throws Exception{}
    
    public void handleBinaryMessage(ChannelHandlerContext context, ByteBufHolder data) throws Exception{
        throw new UnsupportedOperationException("Unsupported message type");
    }
    
    public void handleException(ChannelHandlerContext context, Throwable ex){
        ex.printStackTrace();
        context.close();
    }
    
    public ChannelFuture sendText(ByteBuf text){
        return context.writeAndFlush(new TextWebSocketFrame(text));
    }
    
    public ChannelFuture sendText(ByteBuffer text){
        return context.writeAndFlush(new TextWebSocketFrame(Unpooled.copiedBuffer(text)));
    }
    
    public ChannelFuture sendText(byte[] text){
        return context.writeAndFlush(new TextWebSocketFrame(Unpooled.copiedBuffer(text)));
    }
    
    public ChannelFuture sendText(String text){
        return context.writeAndFlush(new TextWebSocketFrame(text));
    }
    
    public ChannelFuture sendBinary(ByteBuf data){
        return context.writeAndFlush(new BinaryWebSocketFrame(Objects.requireNonNull(data)));
    }
    
    public ChannelFuture sendBinary(ByteBuffer data){
        return sendBinary(Unpooled.copiedBuffer(data));
    }
    
    public ChannelFuture sendBinary(byte[] data){
        return sendBinary(Unpooled.copiedBuffer(data));
    }
    
    private final class Internal extends SimpleChannelInboundHandler<WebSocketFrame>{
        @Override
        protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) throws Exception {
            if(frame instanceof CloseWebSocketFrame){
                handshaker.close(context.channel(), (CloseWebSocketFrame) frame.retain());
                return;
            }
            if(frame instanceof PingWebSocketFrame){
                context.channel().write(new PongWebSocketFrame(frame.content().retain()));
                return;
            }
            if(frame instanceof TextWebSocketFrame){
                handleTextMessage(context, ((TextWebSocketFrame) frame).text());
                return;
            }
            if(frame instanceof BinaryWebSocketFrame){
                handleBinaryMessage(context, frame);
                return;
            }
            throw new UnsupportedMessageTypeException(frame.getClass().getName());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable ex) throws Exception {
            handleException(context, ex);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            super.channelInactive(context);
            handleDisconnect(context);
        }
    }
}
