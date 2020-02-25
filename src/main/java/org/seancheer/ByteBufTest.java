package org.seancheer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import jdk.nashorn.internal.runtime.regexp.joni.ast.StateNode;

import java.nio.charset.StandardCharsets;

public class ByteBufTest {
    public static void main(String[] args){

        ByteBuf buf = Unpooled.buffer(1024);
        System.out.println("ReaderIndex:" + buf.readerIndex() + "   WriterIndex:" + buf.writerIndex());
        buf.writeBytes("hello world".getBytes(StandardCharsets.UTF_8));
        buf.readInt();
        //read和write开头的方法是会修改readerIndex和writerIndex的
        System.out.println("ReaderIndex:" + buf.readerIndex() + "   WriterIndex:" + buf.writerIndex());

        buf.setBytes(1, "xxxxxx".getBytes(StandardCharsets.UTF_8));
        buf.getByte(2);
        //get和set开头的方法是不会推进readerIndex和writerIndex的
        System.out.println("ReaderIndex:" + buf.readerIndex() + "   WriterIndex:" + buf.writerIndex());

        ByteBuf header = Unpooled.copiedBuffer("i am header".getBytes(StandardCharsets.UTF_8));
        ByteBuf body = Unpooled.copiedBuffer("i am body".getBytes(StandardCharsets.UTF_8));
        CompositeByteBuf msg = Unpooled.compositeBuffer();
        msg.addComponent(header).addComponent(body);
        //netty提供了一种composite的ByteBuf，如果组装消息报文的时候，会需要重复使用header，使用这种ByteBuf可以避免每个消息都申请一遍
        //header
        msg.removeComponent(1);
        for(ByteBuf item : msg){
            System.out.println(item.toString(StandardCharsets.UTF_8));
        }

        buf = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));
        ByteBuf dupBuf = buf.duplicate();
        dupBuf.setByte(1, 'x');
        //会发现duplicate返回的buf被修改了，originBuf也会被修改，注意，netty的duplicate并不是真正的复制，他们使用的是同一片内存
        System.out.println("originBuf:" + buf.toString(StandardCharsets.UTF_8) + "    dupBuf:" + dupBuf.toString(StandardCharsets.UTF_8));

        buf = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));
        ByteBuf slicedBuf = buf.slice(3, buf.capacity() - 3);
        slicedBuf.setByte(0, 'z');
        //会发现slice返回的buf被修改了，originBuf也会被修改
        System.out.println("originBuf:" + buf.toString(StandardCharsets.UTF_8) + "    slicedBuf:" + slicedBuf.toString(StandardCharsets.UTF_8));

        buf = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));
        ByteBuf copiedBuf = buf.copy();
        copiedBuf.setByte(0, 'x');
        //会发现copy返回的buf被修改了，originBuf不受影响，所以，copy才是真正的拷贝数据
        System.out.println("originBuf:" + buf.toString(StandardCharsets.UTF_8) + "    copiedBuf:" + copiedBuf.toString(StandardCharsets.UTF_8));

    }
}
