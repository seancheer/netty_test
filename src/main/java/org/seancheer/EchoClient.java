package org.seancheer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ResourceLeakDetector;

import java.lang.ref.PhantomReference;
import java.net.InetSocketAddress;

/**
 * netty client
 */
public class EchoClient {
    private final int port = 12306;
    private final String server = "127.0.0.1";

    public static void main(String[] args) throws InterruptedException {
        new EchoClient().start();
    }


    /**
     * start a client
     * @throws InterruptedException
     */
    public void start() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        //client使用的ServerBootstrap，客户端直接使用Bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group).channel(NioSocketChannel.class).remoteAddress(new InetSocketAddress(server, port))
                .handler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new EchoClientHandler())
                                //Inbound的顺序按照pipeline加入的顺序来进行，Outbound按照加入的顺序的反向来进行
                                .addLast(new OutboundHandler1()).addLast(new OUtboundHandler2());
                    }
                });

        try {
            //client调用的是connect，服务器调用的是bind
            ChannelFuture future =  bootstrap.connect().sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            group.shutdownGracefully().sync();
        }
    }

    /**
     * client handler
     */
    static class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf>{

        /**
         * 当链接一开始，便开始向服务器发送一个字符串消息
         * @param ctx
         * @throws Exception
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.writeAndFlush(Unpooled.copiedBuffer("Netty rocks", CharsetUtil.UTF_8));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            System.out.println("Client Recevied:" + msg.toString(CharsetUtil.UTF_8));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }



    /**
     * output bound handler1
     */
    @ChannelHandler.Sharable
    static class OutboundHandler1 extends ChannelOutboundHandlerAdapter {
        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            System.out.println("read in OutboundHandler1");
            super.read(ctx);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("write in OutboundHandler1");
            super.write(ctx, msg, promise);
        }
    }


    /**
     * output bound handler2
     */
    @ChannelHandler.Sharable
    static class OUtboundHandler2 extends ChannelOutboundHandlerAdapter{
        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            System.out.println("read in OUtboundHandler2");
            super.read(ctx);
        }


        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("write in OUtboundHandler2");
            super.write(ctx, msg, promise);
        }
    }
}
