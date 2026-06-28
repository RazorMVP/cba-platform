package com.cba.fep;

import com.cba.fep.hsm.HsmAdapter;
import com.cba.fep.iso.IsoMessageFactory;
import com.cba.fep.router.MessageRouter;
import com.cba.fep.scheme.SchemeAdapterFactory;
import com.cba.fep.scheme.SchemeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-boot test for the fep-service Spring application.
 *
 * <p>Until the scheme-packager XMLs were corrected (Session 120 cont. 15),
 * {@link IsoMessageFactory} — a {@code @Component} that builds a jPOS
 * {@code GenericPackager} per scheme at construction — threw on every startup,
 * so this context had <b>never successfully booted</b>. This is the first test
 * to start it, and it's the guard against a regression silently breaking the
 * packager XMLs again.
 *
 * <p>fep-service has no database, Flyway, or security, so no Testcontainers are
 * needed. The only boot side effect is the Netty ISO 8583 TCP server
 * ({@code FepTcpServer} {@code @PostConstruct} binds a port); it is bound to an
 * ephemeral port here ({@code fep.tcp.port=0}) to avoid a fixed-8583 conflict,
 * and the embedded HTTP server uses a random port — so the full production
 * wiring (web + Netty + packagers + scheme adapters + HSM) actually starts.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "fep.tcp.port=0")
class FepContextLoadTest {

    @Autowired ApplicationContext context;
    @Autowired IsoMessageFactory isoMessageFactory;   // the bean whose broken XMLs blocked boot
    @Autowired MessageRouter messageRouter;
    @Autowired SchemeAdapterFactory schemeAdapterFactory;
    @Autowired HsmAdapter hsmAdapter;                  // SoftwareHsmAdapter (matchIfMissing=true)

    @Test
    @DisplayName("the full fep-service context boots — packagers load, Netty binds, all beans wire")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(30);

        // IsoMessageFactory constructed → every scheme packager parsed from the
        // (now jPOS-valid) XMLs with no network DTD fetch.
        assertThat(isoMessageFactory.getBasePackager()).isNotNull();
        for (SchemeType s : new SchemeType[]{
                SchemeType.VISA, SchemeType.MASTERCARD, SchemeType.VERVE,
                SchemeType.AFRIGO, SchemeType.UNIONPAY}) {
            assertThat(isoMessageFactory.getPackager(s)).as("packager for %s", s).isNotNull();
        }

        assertThat(messageRouter).isNotNull();
        assertThat(schemeAdapterFactory).isNotNull();
        assertThat(hsmAdapter).isNotNull();
    }
}
