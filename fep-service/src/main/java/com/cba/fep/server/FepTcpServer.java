package com.cba.fep.server;

import com.cba.fep.iso.IsoMessageFactory;
import com.cba.fep.router.MessageRouter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ISO 8583 TCP socket server.
 *
 * <p>Listens on the configured TCP port (default 8583) for incoming connections
 * from ATMs, POS terminals, and the Angular terminal simulator.
 *
 * <p>Uses two separate {@link NioEventLoopGroup}s per Netty best practice:
 * <ul>
 *   <li>{@code bossGroup} — 1 thread; accepts new connections and hands them off</li>
 *   <li>{@code workerGroup} — CPU × 2 threads; handles I/O on accepted connections</li>
 * </ul>
 *
 * <p>TCP socket options:
 * <ul>
 *   <li>{@code SO_BACKLOG=128} — queued connection requests before new ones are refused</li>
 *   <li>{@code SO_KEEPALIVE=true} — detects dead ATM/POS connections using OS keepalive</li>
 *   <li>{@code TCP_NODELAY=true} — disables Nagle algorithm; ISO 8583 messages must be
 *       sent immediately without buffering (low-latency requirement)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FepTcpServer {

    @Value("${fep.tcp.port:8583}")
    private int tcpPort;

    private final IsoMessageFactory messageFactory;
    private final MessageRouter     messageRouter;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel        serverChannel;

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup   = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG,   128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY,  true)
                .childHandler(new FepServerInitializer(messageFactory, messageRouter));

        serverChannel = bootstrap.bind(tcpPort).sync().channel();
        log.info("FEP TCP server started on port {}", tcpPort);
    }

    @PreDestroy
    public void stop() {
        log.info("Shutting down FEP TCP server...");
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        log.info("FEP TCP server stopped.");
    }
}
