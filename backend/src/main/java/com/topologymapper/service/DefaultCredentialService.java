package com.topologymapper.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.topologymapper.model.CredentialFinding;
import com.topologymapper.model.OpenPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class DefaultCredentialService {

    // Common default credentials tried on each service
    private static final List<String[]> HTTP_CREDS = List.of(
        new String[]{"admin",  "admin"},
        new String[]{"admin",  "password"},
        new String[]{"admin",  "1234"},
        new String[]{"admin",  ""},
        new String[]{"root",   "root"},
        new String[]{"root",   ""}
    );

    private static final List<String[]> SSH_CREDS = List.of(
        new String[]{"admin",  "admin"},
        new String[]{"root",   "root"},
        new String[]{"root",   "password"},
        new String[]{"admin",  "password"},
        new String[]{"pi",     "raspberry"}
    );

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public List<CredentialFinding> test(String ip, List<OpenPort> openPorts) {
        List<CredentialFinding> findings = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<List<CredentialFinding>>> tasks = new ArrayList<>();

        for (OpenPort port : openPorts) {
            tasks.add(pool.submit(() -> switch (port.getService()) {
                case "HTTP", "HTTP-alt" -> testHttpBasicAuth(ip, port.getPort());
                case "SSH"              -> testSsh(ip, port.getPort());
                case "FTP"              -> testFtp(ip, port.getPort());
                case "Telnet"           -> testTelnet(ip, port.getPort());
                case "Redis"            -> testRedisNoAuth(ip, port.getPort());
                case "MongoDB"          -> testMongoNoAuth(ip, port.getPort());
                case "Elasticsearch"    -> testElasticNoAuth(ip, port.getPort());
                default                 -> List.<CredentialFinding>of();
            }));
        }
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        for (Future<List<CredentialFinding>> t : tasks) {
            try { findings.addAll(t.get()); } catch (Exception ignored) {}
        }
        return findings;
    }

    // ---- HTTP Basic Auth --------------------------------------------------

    private List<CredentialFinding> testHttpBasicAuth(String ip, int port) {
        String base = "http://" + ip + ":" + port + "/";
        try {
            // First check if auth is required at all
            HttpRequest probe = HttpRequest.newBuilder(URI.create(base))
                .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<Void> r = HTTP_CLIENT.send(probe, HttpResponse.BodyHandlers.discarding());
            int status = r.statusCode();

            if (status == 200 || status == 302) {
                return List.of(CredentialFinding.builder()
                    .port(port).service("HTTP").vulnerable(true)
                    .note("Web interface accessible without authentication (HTTP " + status + ")")
                    .build());
            }
            if (status != 401) return List.of(); // not Basic Auth protected

            // Try common credentials
            for (String[] cred : HTTP_CREDS) {
                String encoded = Base64.getEncoder().encodeToString(
                    (cred[0] + ":" + cred[1]).getBytes());
                HttpRequest authed = HttpRequest.newBuilder(URI.create(base))
                    .header("Authorization", "Basic " + encoded)
                    .timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<Void> ar = HTTP_CLIENT.send(authed, HttpResponse.BodyHandlers.discarding());
                if (ar.statusCode() == 200 || ar.statusCode() == 302) {
                    return List.of(CredentialFinding.builder()
                        .port(port).service("HTTP").username(cred[0]).password(cred[1])
                        .vulnerable(true)
                        .note("Default credentials accepted by HTTP Basic Auth")
                        .build());
                }
            }
        } catch (Exception e) {
            log.debug("HTTP credential test failed on {}:{} — {}", ip, port, e.getMessage());
        }
        return List.of();
    }

    // ---- SSH ---------------------------------------------------------------

    private List<CredentialFinding> testSsh(String ip, int port) {
        JSch jsch = new JSch();
        for (String[] cred : SSH_CREDS) {
            Session session = null;
            try {
                session = jsch.getSession(cred[0], ip, port);
                session.setPassword(cred[1]);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "password");
                session.setTimeout(3000);
                session.connect(3000);
                session.disconnect();
                return List.of(CredentialFinding.builder()
                    .port(port).service("SSH").username(cred[0]).password(cred[1])
                    .vulnerable(true)
                    .note("Default SSH credentials accepted — immediate remote access risk")
                    .build());
            } catch (Exception ignored) {
                // login failed — try next pair
            } finally {
                if (session != null && session.isConnected()) session.disconnect();
            }
        }
        return List.of();
    }

    // ---- FTP ---------------------------------------------------------------

    private List<CredentialFinding> testFtp(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 2000);
            s.setSoTimeout(2000);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter    out = new PrintWriter(s.getOutputStream(), true);
            in.readLine(); // read banner
            out.println("USER anonymous");
            String resp = in.readLine();
            if (resp != null && resp.startsWith("331")) {
                out.println("PASS anonymous@example.com");
                resp = in.readLine();
                if (resp != null && resp.startsWith("230")) {
                    return List.of(CredentialFinding.builder()
                        .port(port).service("FTP").username("anonymous").password("(any)")
                        .vulnerable(true)
                        .note("FTP anonymous login allowed — anyone can browse files")
                        .build());
                }
            }
        } catch (Exception e) {
            log.debug("FTP credential test failed on {}:{}", ip, port);
        }
        return List.of();
    }

    // ---- Telnet ------------------------------------------------------------

    private List<CredentialFinding> testTelnet(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 2000);
            s.setSoTimeout(2000);
            // Just having Telnet open is already a high-severity finding
            return List.of(CredentialFinding.builder()
                .port(port).service("Telnet").vulnerable(true)
                .note("Telnet is open — credentials transmitted in plaintext over the network")
                .build());
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---- Redis no-auth -----------------------------------------------------

    private List<CredentialFinding> testRedisNoAuth(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 2000);
            s.setSoTimeout(2000);
            PrintWriter    out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out.println("PING");
            String resp = in.readLine();
            if (resp != null && resp.contains("PONG")) {
                return List.of(CredentialFinding.builder()
                    .port(port).service("Redis").vulnerable(true)
                    .note("Redis is accessible without authentication — full DB access exposed")
                    .build());
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    // ---- MongoDB no-auth ---------------------------------------------------

    private List<CredentialFinding> testMongoNoAuth(String ip, int port) {
        // MongoDB wire protocol: send isMaster command, 200 OK means no auth needed
        // Simplified: if we can connect and receive data it's likely open
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 2000);
            s.setSoTimeout(1000);
            byte[] buf = new byte[64];
            int n = s.getInputStream().read(buf);
            if (n > 0) {
                return List.of(CredentialFinding.builder()
                    .port(port).service("MongoDB").vulnerable(true)
                    .note("MongoDB port is open — likely no authentication required")
                    .build());
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    // ---- Elasticsearch no-auth ---------------------------------------------

    private List<CredentialFinding> testElasticNoAuth(String ip, int port) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://" + ip + ":" + port + "/"))
                .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> r = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200 && r.body().contains("cluster_name")) {
                return List.of(CredentialFinding.builder()
                    .port(port).service("Elasticsearch").vulnerable(true)
                    .note("Elasticsearch accessible without authentication — full index access exposed")
                    .build());
            }
        } catch (Exception ignored) {}
        return List.of();
    }
}
