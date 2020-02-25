package org.seancheer.rawnio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * accept
 */
public class ReadWriteRunnable implements Runnable {
    private static final String THREAD_PREFIX = "Client_";
    private static final int BUF_SIZE = 1024;
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private final SocketChannel clientChannel;
    private final int id;
    private ByteBuffer readBuf;
    private ByteBuffer writeBuf;
    private boolean running = true;
    private boolean closed = false;
    private State state = State.READDING;

    public ReadWriteRunnable(int id, SocketChannel clientChannel, ServerSocketChannel serverSocketChannel) throws IOException {
        this.id = id;
        selector = Selector.open();
        this.serverSocketChannel = serverSocketChannel;
        this.clientChannel = clientChannel;
        //一般来讲，通道准备好后立马就可以进行写操作，但是我们需要先读取到数据，在进行写，所以只先注册read
        clientChannel.register(selector, SelectionKey.OP_READ);
        readBuf = ByteBuffer.wrap(new byte[BUF_SIZE]);
        writeBuf = ByteBuffer.wrap(new byte[BUF_SIZE]);
    }

    /**
     * client read and write
     */
    public void run() {
        String threadName = THREAD_PREFIX + id;
        Thread.currentThread().setName(threadName);
        System.out.println(String.format("Client[%d] started...", id));
        SelectionKey key = null;
        Set<SelectionKey> keys;
        Iterator<SelectionKey> iter;

        while(state != State.FINISHED){
            try {
                selector.select();
                keys = selector.selectedKeys();
                iter = keys.iterator();
            } catch (IOException e) {
                e.printStackTrace();
                closeChannel();
                break;
            }

            while(iter.hasNext()){
                key = iter.next();
                iter.remove();
                try {
                    if (!key.isValid()){
                        continue;
                    }
                    if (key.isReadable() && state == State.READDING) {
                        //当网络出现阻塞的时候，可能一次性无法读到所有的数据，严格来讲，这里需要判断读到的数据，如果读到的数据
                        //出现预期的end标志，才进行结束，这里简单进行，发现为0，直接退出
                        readBuf.clear();
                        int readNum = clientChannel.read(readBuf);

                        //如果读到的数据为-1，说明远端已经关闭了连接
                        if(readNum < 0){
                            state = State.FINISHED;
                            break;
                        }
                        //正确的从网络中读取数据的方式，直到读到没有数据可读为止
                        while (readNum > 0) {
                            readNum = clientChannel.read(readBuf);
                        }

                        System.out.println("From client:" + new String(readBuf.array(), StandardCharsets.UTF_8));
                        clientChannel.register(selector, SelectionKey.OP_WRITE);
                        state = State.WRITING;
                    }

                    if (key.isWritable() && state == State.WRITING){
                        writeBuf.clear();
                        writeBuf.put("this is server's message".getBytes(StandardCharsets.UTF_8));
                        //当put或者read完毕后，需要进行flip操作，才可以进行write操作，否则是无法写入数据的
                        writeBuf.flip();

                        while(writeBuf.hasRemaining()){
                            clientChannel.write(writeBuf);
                            if (writeBuf.hasRemaining()){
                                sleepSomeWhile(100);
                            }
                        }
                        state = State.FINISHED;
                    }
                }catch(IOException e){
                    e.printStackTrace();
                    key.cancel();
                    closeChannel();
                    break;
                }
            }
        }
        closeChannel();
    }


    private void closeChannel(){
        if (closed){
            return;
        }

        System.out.println(String.format("Client[%d] finished!", id));
        try {
            clientChannel.close();
            selector.close();
            //调用close之后，该方法会返回false
            if (clientChannel.isOpen()){
                System.out.println(String.format("Client[%d] is open!", id));
            }

            //一旦连接建立后，该方法会一直返回true，无论是否调用close
//            if (clientChannel.isConnected()){
//                System.out.println(String.format("Client[%d] is connected! ", id));
//            }
            running = false;
            closed = true;

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    private void sleepSomeWhile(long mili){
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
