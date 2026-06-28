package com.cba.fep.server;

import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.emv.ArpcGenerator;
import com.cba.fep.emv.ArqcValidator;
import com.cba.fep.emv.EmvDataParser;
import com.cba.fep.hsm.HsmAdapter;
import com.cba.fep.iso.IsoMessageFactory;
import com.cba.fep.router.AuthorizationHandler;
import com.cba.fep.router.MessageRouter;
import com.cba.fep.router.NetworkHandler;
import com.cba.fep.scheme.SchemeAdapter;
import com.cba.fep.scheme.SchemeAdapterFactory;
import com.cba.fep.scheme.SchemeType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.ISO87APackager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end test of the FEP Netty/jPOS socket pipeline over a real TCP socket.
 *
 * <p>Wires the production Netty pipeline ({@link FepServerInitializer} → frame
 * decoder → {@link FepMessageDecoder} → {@link FepMessageHandler} →
 * {@link FepMessageEncoder} → frame prepender) and sends length-framed messages,
 * covering two MTIs through the full router:
 * <ul>
 *   <li>{@code 0800} network-management echo → {@code 0810} RC=00 ({@link NetworkHandler},
 *       no external dependency);</li>
 *   <li>{@code 0100} authorization → {@code 0110} RC=00 + auth code, through a real
 *       {@link AuthorizationHandler} whose {@link CardServiceClient} (and scheme/EMV/HSM
 *       collaborators) are mocked — so the money path is exercised over TCP without a
 *       running card-service.</li>
 * </ul>
 *
 * <p>The packager is jPOS's code-based {@link ISO87APackager} (a standard
 * ISO 8583:1987 ASCII packager) injected via a mocked {@link IsoMessageFactory}.
 * This deliberately avoids the XML packagers, whose DOCTYPE references an external
 * DTD (<a href="http://jpos.org/dtd/packager.dtd">jpos.org</a>) that jPOS 2.1.9's
 * {@code GenericPackager(InputStream)} fetches over the network — unavailable in a
 * network-isolated/CI environment. (That XML-load network dependency is noted
 * separately as a production hardening item.) Because the handlers build the
 * response via {@code request.clone()}, the response inherits this packager and
 * packs cleanly on the way out.
 */
class FepSocketRoundTripTest {

    private static final ISOPackager PACKAGER = new ISO87APackager();

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        // Mock factory: decode bytes with the code-based ISO87A packager (no XML/DTD).
        IsoMessageFactory factory = mock(IsoMessageFactory.class);
        when(factory.unpack(any(byte[].class))).thenAnswer(inv -> {
            ISOMsg m = new ISOMsg();
            m.setPackager(PACKAGER);
            m.unpack((byte[]) inv.getArgument(0));
            return m;
        });

        // Real AuthorizationHandler with mocked collaborators — a non-EMV, non-PIN,
        // non-token 0100 only touches detectScheme/getAdapter + cardServiceClient.authorize
        // (the adapter's applyPackager/finalizeResponse are void no-ops on a mock).
        // mock()/when() without MockitoExtension is lenient, so the auth stubs are fine
        // even when the echo test does not exercise them.
        CardServiceClient cardServiceClient = mock(CardServiceClient.class);
        SchemeAdapterFactory schemeAdapterFactory = mock(SchemeAdapterFactory.class);
        SchemeAdapter adapter = mock(SchemeAdapter.class);
        when(schemeAdapterFactory.detectScheme(any())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(any())).thenReturn(adapter);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));

        AuthorizationHandler authHandler = new AuthorizationHandler(
                cardServiceClient, schemeAdapterFactory,
                mock(HsmAdapter.class), mock(EmvDataParser.class),
                mock(ArqcValidator.class), mock(ArpcGenerator.class));

        // 0100 → authHandler, 0800 → NetworkHandler; financial/reversal unused here.
        MessageRouter router = new MessageRouter(authHandler, null, null, new NetworkHandler());

        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        serverChannel = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new FepServerInitializer(factory, router))
                .bind(0).sync().channel(); // ephemeral port
        port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    @AfterEach
    void stopServer() {
        if (serverChannel != null) serverChannel.close().syncUninterruptibly();
        if (worker != null) worker.shutdownGracefully().syncUninterruptibly();
        if (boss != null) boss.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    @DisplayName("an 0800 echo over TCP returns 0810 RC=00 through the full Netty pipeline")
    void echoRoundTrip() throws Exception {
        ISOMsg request = new ISOMsg();
        request.setPackager(PACKAGER);
        request.setMTI("0800");
        request.set(11, "000777");  // STAN
        request.set(70, "301");     // network management code = echo

        ISOMsg response = sendAndReceive(request);

        assertThat(response.getMTI()).isEqualTo("0810");
        assertThat(response.getString(39)).isEqualTo("00");      // approved/acknowledged
        assertThat(response.getString(11)).isEqualTo("000777");  // STAN echoed
    }

    @Test
    @DisplayName("a 0100 authorization over TCP returns 0110 RC=00 + auth code through the full pipeline")
    void authorizationRoundTrip() throws Exception {
        ISOMsg request = new ISOMsg();
        request.setPackager(PACKAGER);
        request.setMTI("0100");
        request.set(2, "4111111111111111");   // PAN (VISA test BIN → not a token)
        request.set(3, "000000");             // processing code = purchase
        request.set(4, "000000010000");       // amount = 100.00
        request.set(11, "000123");            // STAN
        request.set(41, "TERM0001");          // terminal id
        request.set(49, "840");               // currency = USD

        ISOMsg response = sendAndReceive(request);

        assertThat(response.getMTI()).isEqualTo("0110");
        assertThat(response.getString(39)).isEqualTo("00");      // approved
        assertThat(response.getString(38)).isEqualTo("A1B2C3");  // auth code (DE38)
        assertThat(response.getString(11)).isEqualTo("000123");  // STAN echoed
    }

    /** Frames + sends the request over a real socket and reads back the framed response. */
    private ISOMsg sendAndReceive(ISOMsg request) throws Exception {
        byte[] packed = request.pack();
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5_000);

            OutputStream out = socket.getOutputStream();
            out.write((packed.length >> 8) & 0xFF);   // 2-byte big-endian length prefix
            out.write(packed.length & 0xFF);
            out.write(packed);
            out.flush();

            InputStream in = socket.getInputStream();
            int hi = in.read();
            int lo = in.read();
            assertThat(hi).as("response received").isNotEqualTo(-1);
            int len = ((hi & 0xFF) << 8) | (lo & 0xFF);
            byte[] respBytes = in.readNBytes(len);
            assertThat(respBytes).hasSize(len);

            ISOMsg response = new ISOMsg();
            response.setPackager(PACKAGER);
            response.unpack(respBytes);
            return response;
        }
    }
}
