package org.seancheer.rawnio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 *直接使用原生的Nio socket进行编程
 */
public class NioServerSocketTest {
    private Selector selector;
    public static void main(String[] args){
        new NioServerSocketTest().startServer();
    }


    public void startServer()  {
        final String ip = "127.0.0.1";
        final int port = 12306;
        ServerSocketChannel serverChannel;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            ServerSocket socket = serverChannel.socket();
            socket.bind(new InetSocketAddress(ip, port));

            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        }catch(IOException e){
            e.printStackTrace();
            return;
        }

        int i = 0;
        for(;;) {
            try {
                selector.select();
            }catch(IOException e){
                e.printStackTrace();
                closeChannel(serverChannel);
            }

            Set<SelectionKey> keys =  selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while(iter.hasNext()){
                SelectionKey key = null;
                try {
                    key = iter.next();
                    iter.remove();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                        SocketChannel clientChannel = serverSocketChannel.accept();
                        clientChannel.configureBlocking(false);
                        new Thread(new ReadWriteRunnable(i++, clientChannel, serverSocketChannel)).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    key.cancel();
                    closeChannel(key.channel());
                }
            }
        }

    }


    /**
     * close a channel
     * @param channel
     */
    public void closeChannel(SelectableChannel channel){
        if (null != channel){
            try {
                channel.close();
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
