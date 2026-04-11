package com.cba.fep.server;

import com.cba.fep.iso.IsoMessageFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;

import java.util.List;

/**
 * Netty decoder: raw bytes → {@link ISOMsg}.
 *
 * <p>At this stage the scheme is not yet known (BIN lookup has not happened),
 * so we use the base packager. The {@link com.cba.fep.router.MessageRouter}
 * will re-pack with the scheme-specific packager after BIN resolution.
 *
 * <p>The {@link io.netty.handler.codec.LengthFieldBasedFrameDecoder} upstream
 * already stripped the 2-byte length prefix and delivered an exact frame,
 * so {@code msg} is a {@link ByteBuf} containing exactly one ISO 8583 message.
 */
@Slf4j
@RequiredArgsConstructor
public class FepMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final IsoMessageFactory messageFactory;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);

        try {
            ISOMsg isoMsg = messageFactory.unpack(bytes);
            log.debug("Decoded MTI={} STAN={} from {}",
                    isoMsg.getMTI(), isoMsg.getString(11), ctx.channel().remoteAddress());
            out.add(isoMsg);
        } catch (Exception e) {
            log.error("Failed to decode ISO 8583 message from {}: {}",
                    ctx.channel().remoteAddress(), e.getMessage());
            // Drop the frame — do not close the channel; ATM/POS retries
        }
    }
}
