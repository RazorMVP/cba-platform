package com.cba.fep.server;

import com.cba.fep.iso.IsoMessageFactory;
import com.cba.fep.router.MessageRouter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.RequiredArgsConstructor;

/**
 * Netty channel pipeline initializer for ISO 8583 connections.
 *
 * <p>Pipeline (inbound order):
 * <ol>
 *   <li>{@link LengthFieldBasedFrameDecoder} — strips the 2-byte big-endian length prefix
 *       and delivers exactly one ISO 8583 frame per read</li>
 *   <li>{@link FepMessageDecoder} — unpacks raw bytes into {@link org.jpos.iso.ISOMsg}</li>
 *   <li>{@link FepMessageHandler} — routes the message and triggers the response write</li>
 * </ol>
 *
 * <p>Pipeline (outbound order):
 * <ol>
 *   <li>{@link FepMessageEncoder} — packs {@link org.jpos.iso.ISOMsg} into raw bytes</li>
 *   <li>{@link LengthFieldPrepender} — prepends the 2-byte big-endian length</li>
 * </ol>
 *
 * <p>Frame format:
 * <pre>
 *   [2 bytes length] [N bytes ISO 8583 message]
 * </pre>
 * where length = N (does NOT include the 2-byte length header itself).
 * This is the standard TPDU framing used by virtually all ATM/POS switch connections.
 *
 * <p>Max frame size of 65,535 bytes accommodates the largest ISO 8583 messages
 * including EMV data in DE55 (up to 999 bytes) and extended private fields.
 */
@RequiredArgsConstructor
public class FepServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_FRAME_LENGTH     = 65_535;
    private static final int LENGTH_FIELD_OFFSET  = 0;
    private static final int LENGTH_FIELD_LENGTH  = 2;   // 2-byte big-endian length
    private static final int LENGTH_ADJUSTMENT    = 0;
    private static final int INITIAL_BYTES_STRIP  = 2;   // strip the length header

    private final IsoMessageFactory messageFactory;
    private final MessageRouter     messageRouter;

    @Override
    protected void initChannel(SocketChannel ch) {
        // IMPORTANT: outbound handlers (encoder + length prepender) must be added
        // BEFORE the inbound business handler. FepMessageHandler responds with
        // ctx.writeAndFlush(...), whose outbound event flows from that handler toward
        // the head — so it only traverses outbound handlers positioned ahead of it.
        // If the encoders were added after fepHandler (toward the tail), the response
        // ISOMsg would never be encoded/framed and the client would time out.
        ch.pipeline()
            // --- Outbound (response path: ISOMsg → bytes → 2-byte length prefix) ---
            .addLast("framePrepender", new LengthFieldPrepender(LENGTH_FIELD_LENGTH))
            .addLast("isoEncoder",   new FepMessageEncoder())
            // --- Inbound (request path: strip length → bytes → ISOMsg → route) ---
            .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                    MAX_FRAME_LENGTH,
                    LENGTH_FIELD_OFFSET,
                    LENGTH_FIELD_LENGTH,
                    LENGTH_ADJUSTMENT,
                    INITIAL_BYTES_STRIP))
            .addLast("isoDecoder",   new FepMessageDecoder(messageFactory))
            .addLast("fepHandler",   new FepMessageHandler(messageRouter));
    }
}
