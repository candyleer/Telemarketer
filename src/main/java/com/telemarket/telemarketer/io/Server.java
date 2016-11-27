package com.telemarket.telemarketer.io;

import com.telemarket.telemarketer.Connector;
import com.telemarket.telemarketer.context.StartArgs;
import com.telemarket.telemarketer.http.responses.Response;
import com.telemarket.telemarketer.services.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chen Yijie on 2016/11/27 17:14.
 */
public class Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
    private final StartArgs startArgs;
    private ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private Selector selector;

    public Server(StartArgs startArgs) {
        this.startArgs = startArgs;
    }

    public void start() {
        init();
        while (true) {
            try {
                if (selector.select() == 0) {
                    continue;
                }
            } catch (IOException e) {
                LOGGER.error("selector错误", e);
                break;
            }
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                try {
                    iterator.remove();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
                        SocketChannel client = serverSocket.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                    } else if (key.isWritable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        Response response = (Response) key.attachment();
                        ByteBuffer byteBuffer = response.getByteBuffer();
                        if (byteBuffer.hasRemaining()) {
                            client.write(byteBuffer);
                        }
                        if (!byteBuffer.hasRemaining()) {
                            key.cancel();
                            client.close();
                        }
                    } else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        executor.execute(new Connector(client, selector));
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                    }
                } catch (Exception e) {
                    LOGGER.error("socket channel 出错了", e);
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private void init() {
        long start = System.currentTimeMillis();
        System.setProperty("logback.configurationFile", "conf/logback-tele.xml");
        ServerSocketChannel serverChannel;
        try {
            ServiceRegistry.registerServices(startArgs);
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(startArgs.getIp(), startArgs.getPort()));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            LOGGER.error("初始化错误", e);
            System.exit(1);
        }
        LOGGER.info("服务器启动 http://{}:{}/ ,耗时{}ms", startArgs.getIp(), startArgs.getPort(), System.currentTimeMillis() - start);
    }
}