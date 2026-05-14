package com.topologymapper.service;

import com.topologymapper.model.OpenPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PortScanService {

    private static final int[] PORTS = {
        21, 22, 23, 25, 53, 80, 110, 143, 443, 445,
        3306, 3389, 5432, 5900, 6379, 8080, 8443,
        9200, 27017, 1883, 554
    };

    private static final Map<Integer, String> SERVICE_MAP = Map.ofEntries(
        Map.entry(21,    "FTP"),
        Map.entry(22,    "SSH"),
        Map.entry(23,    "Telnet"),
        Map.entry(25,    "SMTP"),
        Map.entry(53,    "DNS"),
        Map.entry(80,    "HTTP"),
        Map.entry(110,   "POP3"),
        Map.entry(143,   "IMAP"),
        Map.entry(443,   "HTTPS"),
        Map.entry(445,   "SMB"),
        Map.entry(3306,  "MySQL"),
        Map.entry(3389,  "RDP"),
        Map.entry(5432,  "PostgreSQL"),
        Map.entry(5900,  "VNC"),
        Map.entry(6379,  "Redis"),
        Map.entry(8080,  "HTTP-alt"),
        Map.entry(8443,  "HTTPS-alt"),
        Map.entry(9200,  "Elasticsearch"),
        Map.entry(27017, "MongoDB"),
        Map.entry(1883,  "MQTT"),
        Map.entry(554,   "RTSP")
    );

    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS    = 1000;

    public List<OpenPort> scan(String ip) {
        ExecutorService pool = Executors.newFixedThreadPool(21);
        List<Future<OpenPort>> futures = new ArrayList<>();
        for (int port : PORTS) {
            final int p = port;
            futures.add(pool.submit(() -> probePort(ip, p)));
        }
        pool.shutdown();
        try { pool.awaitTermination(20, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        List<OpenPort> open = futures.stream()
            .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(OpenPort::getPort))
            .collect(Collectors.toList());

        log.info("Port scan on {} — {} open ports: {}", ip, open.size(),
            open.stream().map(p -> String.valueOf(p.getPort())).collect(Collectors.joining(",")));
        return open;
    }

    private OpenPort probePort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            String service = SERVICE_MAP.getOrDefault(port, "Unknown");
            String banner  = grabBanner(socket, port, ip);
            String version = parseVersion(banner, port);
            return OpenPort.builder()
                .port(port).service(service).banner(banner).version(version)
                .build();
        } catch (Exception e) {
            return null;
        }
    }

    private String grabBanner(Socket socket, int port, String ip) {
        try {
            OutputStream out = socket.getOutputStream();
            InputStream  in  = socket.getInputStream();

            if (port == 80 || port == 8080 || port == 8443) {
                String req = "HEAD / HTTP/1.0\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n";
                out.write(req.getBytes());
                out.flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("server:")) {
                        return line.substring(7).trim();
                    }
                    sb.append(line).append(" ");
                    if (sb.length() > 200) break;
                }
                return "HTTP";
            } else if (port == 6379) {
                // Redis: send INFO command
                out.write("*1\r\n$4\r\nINFO\r\n".getBytes());
                out.flush();
            }

            // For SSH, FTP, Telnet, SMTP, Redis etc — banner is sent immediately
            byte[] buf = new byte[256];
            int    n   = in.read(buf, 0, 256);
            if (n > 0) {
                return new String(buf, 0, n).trim()
                    .replaceAll("[\\r\\n]+", " ")
                    .substring(0, Math.min(100, n));
            }
        } catch (Exception ignored) {}
        return SERVICE_MAP.getOrDefault(port, "");
    }

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("([A-Za-z][A-Za-z0-9_.-]+)[/\\s]([0-9]+\\.[0-9]+[.\\w]*)");

    private String parseVersion(String banner, int port) {
        if (banner == null || banner.isEmpty()) return null;
        if (port == 22) {
            // SSH-2.0-OpenSSH_8.9p1 Ubuntu-3ubuntu0.6
            Matcher m = Pattern.compile("SSH-[\\d.]+-(.+)").matcher(banner);
            if (m.find()) return m.group(1).split("\\s")[0];
        }
        Matcher m = VERSION_PATTERN.matcher(banner);
        if (m.find()) return m.group(1) + "/" + m.group(2);
        return null;
    }
}
