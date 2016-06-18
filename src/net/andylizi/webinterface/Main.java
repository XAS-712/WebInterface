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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.InetAddress;

public final class Main extends JavaPlugin{
    private final EventLoopGroup group;
    private final ServerBootstrap bootstrap;
    private ChannelFuture channel;

    public Main() {
        group = new NioEventLoopGroup(2, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();
            private final ThreadGroup threadGroup = new ThreadGroup("WebInterface-EventGroup");
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(threadGroup, r, "WebInterface-EventGroup-#"+counter.getAndIncrement());
                return thread;
            }
        });
        try{
            bootstrap = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline()
                                    .addLast("http-decoder", new HttpRequestDecoder())
                                    .addLast("http-aggregator", new HttpObjectAggregator(65536))
                                    .addLast("http-encoder", new HttpResponseEncoder())
                                    .addLast("http-chunked", new ChunkedWriteHandler())
                                    .addLast("http-handler", new ServerHandler(Main.this));
                        }
                    });
        }catch(Throwable t){
            group.shutdownGracefully();
            throw t;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        String ip = getConfig().getString("network.ip", "").trim();
        int port = getConfig().getInt("network.port");
        InetSocketAddress address;
        try{
            if(ip.isEmpty() || ip.equals("*")){
                address = new InetSocketAddress(port);
                ip = "*";
            }else
                address = new InetSocketAddress(InetAddress.getByName(ip), port);
        }catch(IllegalArgumentException ex){
            getLogger().warning("端口设定超出有效范围, 插件无法加载!");
            ex.printStackTrace();
            setEnabled(false);
            return;
        } catch(UnknownHostException ex) {
            getLogger().warning("无效的IP地址, 插件无法加载!");
            ex.printStackTrace();
            setEnabled(false);
            return;
        }
        try{
            channel = bootstrap.bind(address).sync();
            getLogger().warning("在 "+ip+':'+port+" 开始监听...");
        }catch(Exception ex){
            getLogger().warning("无法绑定到端口 "+port+"!");
            ex.printStackTrace();
            setEnabled(false);
            return;
        }
    }

    @Override
    public void onDisable() {
        if(group != null)
            group.shutdownGracefully();
    }
}
