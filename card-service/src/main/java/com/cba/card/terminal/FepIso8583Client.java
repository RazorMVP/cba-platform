package com.cba.card.terminal;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Netty TCP client that sends ISO 8583 messages to the FEP (port 8583).
 *
 * <p>Uses the same 2-byte big-endian length prefix framing as the FEP server:
 * <pre>
 *   [2 bytes length] [N bytes ISO 8583 message]
 * </pre>
 *
 * <p>Each {@code send()} call establishes a short-lived TCP connection, sends
 * the message, and waits for a single response. This is intentionally simple
 * (one connection per transaction) — a production system would use a connection
 * pool, but for the terminal simulator this is appropriate.
 */
@Slf4j
@Component
public class FepIso8583Client {

    @Value("${fep.host:localhost}")
    private String fepHost;

    @Value("${fep.tcp-port:8583}")
    private int fepPort;

    @Value("${fep.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${fep.read-timeout-ms:30000}")
    private int readTimeoutMs;

    /**
     * Sends raw ISO 8583 bytes to the FEP and returns the raw response bytes.
     *
     * @param messageBytes the raw ISO 8583 message (without the 2-byte length prefix)
     * @return raw response bytes from the FEP (without the length prefix)
     * @throws FepConnectionException if the FEP is unreachable or times out
     */
    public byte[] send(byte[] messageBytes) {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();

        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    // Inbound: strip 2-byte length prefix, expose body
                                    .addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2))
                                    // Outbound: prepend 2-byte length prefix
                                    .addLast(new LengthFieldPrepender(2))
                                    .addLast(new ResponseHandler(responseFuture));
                        }
                    });

            ChannelFuture connectFuture = bootstrap.connect(fepHost, fepPort).sync();
            Channel channel = connectFuture.channel();

            // Write message
            ByteBuf buf = Unpooled.wrappedBuffer(messageBytes);
            channel.writeAndFlush(buf);

            // Wait for response
            byte[] response = responseFuture.get(readTimeoutMs, TimeUnit.MILLISECONDS);
            channel.close().sync();
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FepConnectionException("Interrupted while waiting for FEP response", e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new FepConnectionException(
                    "FEP response timeout after " + readTimeoutMs + "ms", e);
        } catch (Exception e) {
            throw new FepConnectionException("FEP communication error: " + e.getMessage(), e);
        } finally {
            group.shutdownGracefully();
        }
    }

    /** Netty handler that captures the first inbound frame into the future. */
    @ChannelHandler.Sharable
    private static class ResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final CompletableFuture<byte[]> future;

        ResponseHandler(CompletableFuture<byte[]> future) {
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            future.complete(bytes);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }
    }

    /** Thrown when the FEP TCP socket cannot be reached or times out. */
    public static class FepConnectionException extends RuntimeException {
        public FepConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
