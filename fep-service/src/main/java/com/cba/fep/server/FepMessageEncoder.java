package com.cba.fep.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;

/**
 * Netty encoder: {@link ISOMsg} → raw bytes.
 *
 * <p>The {@link io.netty.handler.codec.LengthFieldPrepender} downstream
 * will prepend the 2-byte length prefix, so this encoder only produces
 * the packed ISO 8583 message bytes (MTI + bitmap + fields).
 *
 * <p>The ISOMsg must already have its packager set (scheme-specific or base)
 * before it reaches this encoder. The router ensures this is the case.
 */
@Slf4j
public class FepMessageEncoder extends MessageToByteEncoder<ISOMsg> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ISOMsg msg, ByteBuf out) throws Exception {
        byte[] packed = msg.pack();
        out.writeBytes(packed);
        log.debug("Encoded MTI={} STAN={} ({} bytes) to {}",
                msg.getMTI(), msg.getString(11), packed.length, ctx.channel().remoteAddress());
    }
}
