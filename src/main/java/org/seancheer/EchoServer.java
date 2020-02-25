package org.seancheer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.seancheer.protobuf.CmdMessage;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * main
 */
public class EchoServer {
    private final int port = 12345;
    private final String ip = "127.0.0.1";


    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        new EchoServer().start();
    }

    /**
     * start a netty server.
     */
    public void start() throws InterruptedException {
        final EchoServerHandler serverHandler = new EchoServerHandler();
        EventLoopGroup group = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group).channel(NioServerSocketChannel.class).localAddress(new InetSocketAddress(ip, port))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //将IdleStateHandler作为第一个handler
                        ch.pipeline().addLast(new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS),
                                serverHandler);
//                        ch.pipeline().addLast(
//                                new ProtobufVarint32FrameDecoder(),//负责解析protobuf前端的长度信息，根据报文的长度信息动态的分割报文
//                                new ProtobufDecoder(CmdMessage.CmdMsg.getDefaultInstance()),//负责收到的protobuf报文，指定message的类型
//                                new ProtobufVarint32LengthFieldPrepender(), //负责在发出去的报文前面增加长度信息
//                                new ProtobufEncoder(),//负责对发出去的报文进行编码
//                                new ProtobufInboundHandler());//负责处理入站的业务逻辑
                    }
                });

        try {
            ChannelFuture future = bootstrap.bind().sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //channel关闭完成后需要安全的关闭EventLoopGroup，因为这个本质上是一个线程池，所以需要优雅的释放资源。
            group.shutdownGracefully().sync();
        }
    }

    /**
     * 负责处理protobuf的业务罗技
     */
    static class ProtobufInboundHandler extends SimpleChannelInboundHandler{

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            CmdMessage.CmdMsg cmdMsg = (CmdMessage.CmdMsg)(msg);
            //do something

            //build new message to client
            CmdMessage.CmdMsg out = CmdMessage.CmdMsg.newBuilder().setLength(8).setType(12).build();
            ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    /**
     * 简易的echo服务器
     * sharable表示该对象可以被多个线程进行共享
     */
    @ChannelHandler.Sharable
    static class EchoServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf inMsg = (ByteBuf) msg;
            System.out.println("Server recevied:" + inMsg.toString(CharsetUtil.UTF_8));
            ctx.write(inMsg);
        }


        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            cause.printStackTrace();
            ctx.close();
        }
    }

    /**
     * 一个简易的解码器
     */
    @ChannelHandler.Sharable
    static class IntMessageDecoder extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            /**
             * 在网络处理里面需要注意，必须检测当前可读的字节数是否够，不够的话，直接读取会抛出异常。
             */
            if (in.readableBytes() >= 4) {
                //使用完成后不需要进行release，编解码器会自行调用的。
                out.add(in.readInt());
            }
        }
    }

    /**
     * 一个简易的编码器
     */
    @ChannelHandler.Sharable
    static class IntMessageEncoder extends MessageToByteEncoder<Integer>{

        @Override
        protected void encode(ChannelHandlerContext ctx, Integer msg, ByteBuf out) throws Exception {
            //可直接调用writeInt来写入对应的消息
            out.writeInt(msg);
        }
    }

    /**
     * 一个简单的利用IdleStateHandler例子
     */
    static class HeartBeatHandler extends ChannelInboundHandlerAdapter{
        //创建一个不会被释放的buffer，相当于final
        private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HEATBEAT", CharsetUtil.UTF_8));
        static final ByteBuf DELIMETER = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("#####", CharsetUtil.UTF_8));

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                //duplicate虽然是共享内存，但是它在duplicate的时候不会拷贝mark，offset等信息，所以这里使用了duplicate
                ctx.channel().writeAndFlush(HEARTBEAT_SEQUENCE.duplicate()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }else{
                super.userEventTriggered(ctx, evt);
            }
        }
    }
}
