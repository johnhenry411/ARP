package com.topologymapper.service;

import com.sun.net.httpserver.*;
import com.topologymapper.model.DnsRule;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class CaptivePortalService {

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    @Autowired private CaService      caService;
    @Autowired private DnsRuleService ruleService;

    private HttpServer  httpServer;
    private HttpsServer httpsServer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() { return running.get(); }

    public synchronized String start(String ip) {
        if (running.get()) return "already running";
        try {
            InetAddress addr = InetAddress.getByName(ip);
            startHttp(addr);
            startHttps(addr);
            running.set(true);
            log.info("Captive portal started on http://{}:80 and https://{}:443", ip, ip);
            return "captive portal started on " + ip;
        } catch (BindException e) {
            return "ERROR: Cannot bind ports 80/443 on " + ip + " — run as Administrator";
        } catch (Exception e) {
            log.error("Captive portal start failed: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    public synchronized void stop() {
        if (!running.get()) return;
        if (httpServer  != null) httpServer.stop(0);
        if (httpsServer != null) httpsServer.stop(0);
        running.set(false);
        log.info("Captive portal stopped");
    }

    // ---- HTTP server -------------------------------------------------------

    private void startHttp(InetAddress addr) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(addr, 80), 20);
        httpServer.createContext("/", exchange -> {
            String host = extractHost(exchange.getRequestHeaders().getFirst("Host"));
            byte[] body = landingPage(host, false).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        httpServer.setExecutor(Executors.newFixedThreadPool(4));
        httpServer.start();
    }

    // ---- HTTPS server with dynamic SNI certs -------------------------------

    private void startHttps(InetAddress addr) throws Exception {
        httpsServer = HttpsServer.create(new InetSocketAddress(addr, 443), 20);
        SSLContext ctx = buildSslContext();
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sp = getSSLContext().getDefaultSSLParameters();
                params.setSSLParameters(sp);
            }
        });
        httpsServer.createContext("/", exchange -> {
            String host = extractHost(exchange.getRequestHeaders().getFirst("Host"));
            byte[] body = landingPage(host, true).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        httpsServer.setExecutor(Executors.newFixedThreadPool(4));
        httpsServer.start();
    }

    private SSLContext buildSslContext() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // We pre-populate with a "default" entry; real serving happens via dynamic manager
        X509Certificate[] defaultChain = caService.getCertChain("localhost");
        ks.setKeyEntry("default", caService.getServerPrivateKey(),
            "changeit".toCharArray(), defaultChain);
        kmf.init(ks, "changeit".toCharArray());

        // Wrap with a dynamic manager that picks the right cert based on SNI
        KeyManager[] managers = { new DynamicKeyManager(
            (X509KeyManager) kmf.getKeyManagers()[0], caService) };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(managers, null, new SecureRandom());
        return ctx;
    }

    // ---- Landing page HTML -------------------------------------------------

    private String landingPage(String hostname, boolean https) {
        DnsRule rule = ruleService.findMatchingRule(hostname);
        String title = rule != null ? rule.getPageTitle() : "Network Redirect";
        String body  = rule != null ? rule.getPageBody()
            : "This request was intercepted by the network.";
        String protocol = https ? "https" : "http";

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>%s</title>
            <style>
              *{margin:0;padding:0;box-sizing:border-box}
              body{background:#050D1A;color:#E0F4FF;font-family:monospace;
                   min-height:100vh;display:flex;align-items:center;justify-content:center}
              .card{background:#0A1628;border:1px solid #0F2744;border-radius:8px;
                    padding:2rem;max-width:480px;width:90%%;text-align:center;
                    box-shadow:0 0 40px rgba(0,217,255,0.08)}
              .icon{font-size:2.5rem;margin-bottom:1rem}
              h1{color:#00D9FF;font-size:1.1rem;margin-bottom:.75rem;
                 text-shadow:0 0 12px rgba(0,217,255,0.4)}
              p{color:#4A7FA5;font-size:.85rem;line-height:1.6;margin-bottom:1rem}
              .domain{color:#FFB800;font-size:.8rem;background:rgba(255,184,0,.08);
                      border:1px solid rgba(255,184,0,.25);border-radius:4px;
                      padding:.3rem .6rem;display:inline-block;margin-bottom:1rem}
              .badge{color:#1E4060;font-size:.7rem;margin-top:1.5rem}
            </style>
            </head>
            <body>
            <div class="card">
              <div class="icon">⌖</div>
              <h1>%s</h1>
              <div class="domain">%s://%s</div>
              <p>%s</p>
              <div class="badge">Intercepted by Topology Mapper · DNS Spoof Demo</div>
            </div>
            </body>
            </html>
            """.formatted(title, title, protocol, hostname, body);
    }

    private static String extractHost(String hostHeader) {
        if (hostHeader == null) return "unknown";
        int colon = hostHeader.lastIndexOf(':');
        return colon > 0 ? hostHeader.substring(0, colon) : hostHeader;
    }

    // ---- Dynamic SNI key manager -------------------------------------------

    private static class DynamicKeyManager extends X509ExtendedKeyManager {
        private final X509KeyManager fallback;
        private final CaService      ca;

        DynamicKeyManager(X509KeyManager fallback, CaService ca) {
            this.fallback = fallback;
            this.ca       = ca;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return sniHostname(socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            try { return ca.getCertChain(alias); }
            catch (Exception e) { return fallback.getCertificateChain("default"); }
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return ca.getServerPrivateKey();
        }

        private static String sniHostname(Socket socket) {
            if (!(socket instanceof SSLSocket)) return "localhost";
            try {
                SSLSocket ssl = (SSLSocket) socket;
                ExtendedSSLSession session = (ExtendedSSLSession) ssl.getHandshakeSession();
                return session.getRequestedServerNames().stream()
                    .filter(n -> n instanceof SNIHostName)
                    .map(n -> ((SNIHostName) n).getAsciiName())
                    .findFirst()
                    .orElse("localhost");
            } catch (Exception e) { return "localhost"; }
        }

        // Required stubs
        @Override public String[] getClientAliases(String kt, Principal[] i) { return null; }
        @Override public String chooseClientAlias(String[] kt, Principal[] i, Socket s) { return null; }
        @Override public String[] getServerAliases(String kt, Principal[] i) { return new String[]{"default"}; }
    }
}
