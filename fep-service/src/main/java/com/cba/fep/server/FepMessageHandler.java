package com.cba.fep.server;

import com.cba.fep.router.MessageRouter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;

/**
 * Netty inbound handler: receives a decoded {@link ISOMsg}, dispatches it
 * to the {@link MessageRouter}, and writes the response back on the same channel.
 *
 * <p>Annotated with {@code @ChannelHandler.Sharable} because this handler is
 * stateless — all state is in the ISOMsg objects and the router/services they call.
 * A single instance is shared across all ATM/POS channel pipelines.
 *
 * <p>If the router returns {@code null} (e.g., a one-way advice with no reply
 * required), no write is performed and the channel stays open.
 */
@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class FepMessageHandler extends SimpleChannelInboundHandler<ISOMsg> {

    private final MessageRouter router;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ISOMsg request) {
        String mti  = safeGet(request, 0);
        String stan = safeGet(request, 11);
        log.info("Received MTI={} STAN={} from {}", mti, stan, ctx.channel().remoteAddress());

        try {
            ISOMsg response = router.route(request);
            if (response != null) {
                ctx.writeAndFlush(response);
                log.info("Sent response MTI={} RC={} STAN={} to {}",
                        safeGet(response, 0), safeGet(response, 39),
                        safeGet(response, 11), ctx.channel().remoteAddress());
            }
        } catch (Exception e) {
            log.error("Unhandled exception routing MTI={} STAN={}: {}", mti, stan, e.getMessage(), e);
            // For authorization requests, send a system error response (RC=96)
            if (mti != null && (mti.startsWith("01") || mti.startsWith("02"))) {
                sendSystemError(ctx, request, stan);
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Terminal connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Terminal disconnected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel error from {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }

    private void sendSystemError(ChannelHandlerContext ctx, ISOMsg request, String stan) {
        try {
            ISOMsg response = (ISOMsg) request.clone();
            String reqMti = request.getMTI();
            // Convert request MTI to response MTI (e.g., 0100 → 0110)
            response.setMTI(reqMti.substring(0, 2) + "1" + reqMti.charAt(3));
            response.set(39, "96"); // System malfunction
            ctx.writeAndFlush(response);
        } catch (Exception ex) {
            log.error("Failed to send system error response for STAN={}: {}", stan, ex.getMessage());
        }
    }

    private String safeGet(ISOMsg msg, int field) {
        try {
            return msg.getString(field);
        } catch (Exception e) {
            return "?";
        }
    }
}
