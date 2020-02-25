package org.seancheer.rawnio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NioClientSocket {
    private final int port = 12306;
    private final String ip = "127.0.0.1";
    private final int BUF_SIZE = 1024;
    private State state = State.WRITING;


    public static void main(String[] args) throws IOException, InterruptedException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        boolean res = channel.connect(new InetSocketAddress("www.baidu.com", 443));
        //阻塞调用会返回true，非阻塞因为直接返回，此时基本上连接都没有建立，所以返回false
        System.out.println("connect res:" + res);
        //阻塞调用会返回true，非阻塞因为直接返回，如果连接建立好了，那么返回true，否则返回false。
        while(!res) {
            res = channel.finishConnect();
            System.out.println("finishConnect res:" + res);
            TimeUnit.MILLISECONDS.sleep(200);
        }
        channel.close();

        new NioClientSocket().startClient();
    }

    /**
     * NioClient
     */
    public void startClient() throws IOException {
        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);

        Selector selector = Selector.open();
        clientChannel.register(selector, SelectionKey.OP_CONNECT);
        clientChannel.connect(new InetSocketAddress(ip, port));

        while(state != State.FINISHED) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();
                //这里的判断也很重要
                if (!key.isValid()){
                    continue;
                }

                //注意，这里很重要，对于客户端来讲，必须要进行该操作才能读取到数据，对于阻塞模式，该方法会一直阻塞到链接建立
                //并返回，对于非阻塞模式，如果链接没有建立，返回false，否则返回true。
                if (key.isConnectable() &&  clientChannel.finishConnect()){
                    //客户端首先进行主动写操作
                    clientChannel.register(selector, SelectionKey.OP_WRITE);
                    continue;
                }

                if (key.isReadable() && state == State.READDING) {
                    ByteBuffer buf = ByteBuffer.wrap(new byte[BUF_SIZE]);
                    int readNum = clientChannel.read(buf);

                    //如果读到的数据为-1，说明远端已经关闭了连接
                    if(readNum < 0){
                        state = State.FINISHED;
                        break;
                    }

                    //正确的从网络中读取数据的方式，直到读到没有数据可读为止
                    while (readNum > 0) {
                        readNum = clientChannel.read(buf);
                    }
                    System.out.println("From server:" + new String(buf.array(), StandardCharsets.UTF_8));
                    state = State.FINISHED;
                }

                if (key.isWritable() && state == State.WRITING) {
                    ByteBuffer buf = ByteBuffer.wrap("hello server, from client.".getBytes(StandardCharsets.UTF_8));
                    //一直写数据，直到写完为止，实际项目中可以全局保存写入的buffer，然后继续下一次select，如果可写了，会wakeup出来
                    //不必像现在这样子sleep一会儿继续写
                    while(buf.hasRemaining()){
                        clientChannel.write(buf);
                        if (buf.hasRemaining()){
                            sleepSomeWhile(100);
                        }
                    }

                    state = State.READDING;
                    clientChannel.register(selector, SelectionKey.OP_READ);
                }
            }
        }

        selector.close();
        clientChannel.close();
    }


    private void sleepSomeWhile(long mili){
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
