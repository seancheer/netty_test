package org.seancheer.websocket;

import com.sun.deploy.net.HttpUtils;
import com.sun.javafx.sg.prism.NGTriangleMesh;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * a simple websocket chat server.
 *
 * @author: seancheer
 * @date: 2020/2/25
 **/
public class ChatServer {
    private static final int port = 8080;
    private static final String addr = "127.0.0.1";

    public static void main(String[] args) {
        final ChannelGroup group = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        final NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(nioEventLoopGroup).channel(NioServerSocketChannel.class).childHandler(
                new WebSocketChannelInit(group));
        ChannelFuture future = bootstrap.bind(new InetSocketAddress(addr, port));
        future.syncUninterruptibly();
        Channel channel = future.channel();

        //添加jvm在关闭的时候需要处理的资源回收事项
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            destroy(group, nioEventLoopGroup);
        }));

        channel.closeFuture().syncUninterruptibly();
    }

    /**
     * 回收相关的资源
     *
     * @param group
     * @param eventLoopGroup
     */
    private static void destroy(ChannelGroup group, EventLoopGroup eventLoopGroup) {
        group.close();
        eventLoopGroup.shutdownGracefully();
    }


    /**
     * http reqeust handler
     */
    static class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private static final String wsUri = "/ws";
        private static File indexFile;

        static {
            URL path = HttpRequestHandler.class.getClassLoader().getResource("index.html");
            try {
                indexFile = new File(path.toURI());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }


        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            if (!wsUri.equalsIgnoreCase(request.uri())) {
                //如果需要100，那么发送100给用户
                if (HttpUtil.is100ContinueExpected(request)) {
                    send100Continue(ctx);
                }

                RandomAccessFile file = new RandomAccessFile(indexFile, "r");
                HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8");
                boolean isKeepAlive = HttpUtil.isKeepAlive(request);
                if (isKeepAlive) {
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
                    response.headers().set(HttpHeaderNames.CONNECTION, "");
                }

                ctx.write(response);
                if (ctx.pipeline().get(SslHandler.class) == null) {
                    //如果不需要加密，那么可以直接通过零拷贝的方式把文件发送给用户
                    FileRegion fileRegion = new DefaultFileRegion(file.getChannel(), 0, file.length());
                    ctx.write(fileRegion);
                } else {
                    //否则的话，采用nio的写文件方式，该方式会进行内存拷贝
                    ChunkedNioFile nioFile = new ChunkedNioFile(file.getChannel());
                    ctx.write(nioFile);
                }

                //写入http最后需要的内容
                ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                if (!isKeepAlive) {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            } else {
                //retain是为了保证request在该handler处理完成后不会释放
                ctx.fireChannelRead(request.retain());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }

        /**
         * send 100 continue
         *
         * @param ctx
         */
        private void send100Continue(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
            ctx.write(response);
        }
    }

    /**
     * 处理websocket frame的handler
     */
    static class TextWebSocketFramHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private final ChannelGroup channelGroup;

        public TextWebSocketFramHandler(ChannelGroup group) {
            this.channelGroup = group;
        }

        /**
         * 将客户端传来的信息发送给所有的客户端
         *
         * @param ctx
         * @param msg
         * @throws Exception
         */
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
            channelGroup.writeAndFlush(msg.retain());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel curChannel = ctx.channel();
            channelGroup.remove(curChannel);
            String msg = String.format("Client[%s] has been left.",
                    curChannel.remoteAddress().toString());
            System.out.println(msg);
            channelGroup.writeAndFlush(new TextWebSocketFrame(msg));
//                    .addListener(new ChannelFutureListener() {
//                        @Override
//                        public void operationComplete(ChannelFuture future) throws Exception {
//                            System.out.println("Leaving msg has been send!");
//                        }
//                    });
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                //没有新创建的channel都会被分配一个新的pipeline，所以这里的remove是没有问题的，只是当前已经创建好的websocket channel移除掉了HttpHandler，
                //其他的并不会受到影响
                ctx.pipeline().remove(HttpRequestHandler.class);
                //将加入信息发送给所有的channel
                channelGroup.writeAndFlush(new TextWebSocketFrame("Client " + ctx.channel().remoteAddress().toString() + " joined"));
                //将当前channel加入到channel group中
                channelGroup.add(ctx.channel());
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }

    /**
     * channel init class
     */
    static class WebSocketChannelInit extends ChannelInitializer<Channel> {
        public final ChannelGroup channelGroup;

        public WebSocketChannelInit(ChannelGroup group) {
            this.channelGroup = group;
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast(new HttpServerCodec(),
                    new ChunkedWriteHandler(),
                    new HttpObjectAggregator(64 * 1024),
                    new HttpRequestHandler(),
                    new WebSocketServerProtocolHandler("/ws"),
                    new TextWebSocketFramHandler(channelGroup));
        }
    }
}
