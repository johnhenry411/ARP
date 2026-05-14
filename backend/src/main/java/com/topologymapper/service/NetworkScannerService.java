package com.topologymapper.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NetworkScannerService {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    private static final Pattern LATENCY_WINDOWS = Pattern.compile("time[=<](\\d+)ms");
    private static final Pattern LATENCY_UNIX    = Pattern.compile("time=(\\d+\\.?\\d*) ms");

    // Windows: "  192.168.1.1    a4-2b-8c-11-ff-02    dynamic"
    private static final Pattern ARP_WINDOWS =
            Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9a-fA-F-]{17})\\s+(dynamic|static)");
    // Linux/Mac: "? (192.168.1.1) at a4:2b:8c:11:ff:02 [ether]"
    private static final Pattern ARP_UNIX =
            Pattern.compile("\\((\\d+\\.\\d+\\.\\d+\\.\\d+)\\) at ([0-9a-fA-F:]{17})");

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public PingResult ping(String ip) {
        return ping(ip, 1500);
    }

    public PingResult ping(String ip, int timeoutMs) {
        String[] cmd = IS_WINDOWS
                ? new String[]{"ping", "-n", "1", "-w", String.valueOf(timeoutMs), ip}
                : new String[]{"ping", "-c", "1", "-W", "1", ip};
        try {
            long start = System.currentTimeMillis();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new BufferedReader(new InputStreamReader(proc.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            boolean exited = proc.waitFor(timeoutMs + 500L, TimeUnit.MILLISECONDS);
            if (!exited) { proc.destroyForcibly(); return PingResult.unreachable(); }

            if (proc.exitValue() != 0) return PingResult.unreachable();

            int latency = parseLatency(output);
            if (latency < 0) latency = (int)(System.currentTimeMillis() - start);
            return PingResult.ok(latency);
        } catch (Exception e) {
            log.debug("Ping failed for {}: {}", ip, e.getMessage());
            return PingResult.unreachable();
        }
    }

    /**
     * Tries reverse DNS → mDNS → NetBIOS in sequence, returning the first
     * short hostname found (e.g. "macbook-henry"), or null if all fail.
     */
    public String resolveHostname(String ip) {
        // 1. Reverse DNS (works when a PTR record exists)
        try {
            InetAddress addr = InetAddress.getByName(ip);
            CompletableFuture<String> future = CompletableFuture.supplyAsync(addr::getHostName);
            String name = future.get(2, TimeUnit.SECONDS);
            if (name != null && !name.equals(ip)) {
                return shortName(name);
            }
        } catch (Exception ignored) {}

        // 2. mDNS unicast PTR query (works for Apple/Linux with Bonjour/Avahi)
        String mDns = resolveMDNS(ip);
        if (mDns != null) return mDns;

        // 3. NetBIOS Node Status (works for Windows)
        String netBios = resolveNetBIOS(ip);
        if (netBios != null) return netBios;

        return null;
    }

    // ---------- mDNS --------------------------------------------------------

    private String resolveMDNS(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return null;
        String ptrName = parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0] + ".in-addr.arpa";
        byte[] query = buildDnsQuery(ptrName, 12 /* PTR */);
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(1000);
            InetAddress target = InetAddress.getByName(ip);
            sock.send(new DatagramPacket(query, query.length, target, 5353));
            byte[] buf = new byte[512];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            return parsePtrResponse(resp.getData(), resp.getLength());
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] buildDnsQuery(String name, int type) {
        String[] labels = name.split("\\.");
        int nameLen = 1;
        for (String l : labels) nameLen += 1 + l.length();
        byte[] pkt = new byte[12 + nameLen + 4];
        // Header: ID=0, flags=0, QDCOUNT=1, rest=0
        pkt[5] = 1;
        int pos = 12;
        for (String label : labels) {
            pkt[pos++] = (byte) label.length();
            for (char c : label.toCharArray()) pkt[pos++] = (byte) c;
        }
        pkt[pos++] = 0;          // end of name
        pkt[pos++] = (byte)(type >> 8);
        pkt[pos++] = (byte)(type & 0xFF);
        pkt[pos++] = 0;
        pkt[pos]   = 1;          // class IN
        return pkt;
    }

    private String parsePtrResponse(byte[] data, int len) {
        if (len < 12) return null;
        int anCount = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        if (anCount == 0) return null;
        // Skip header (12) and question section
        int[] pos = {12};
        skipDnsName(data, pos);
        pos[0] += 4; // skip QTYPE + QCLASS
        // Parse first answer
        if (pos[0] >= len) return null;
        skipDnsName(data, pos);
        pos[0] += 2; // type
        pos[0] += 2; // class
        pos[0] += 4; // TTL
        int rdLen = ((data[pos[0]] & 0xFF) << 8) | (data[pos[0]+1] & 0xFF);
        pos[0] += 2;
        if (rdLen <= 0 || pos[0] >= len) return null;
        String ptrTarget = readDnsName(data, pos, len);
        return ptrTarget.isEmpty() ? null : shortName(ptrTarget);
    }

    // ---------- NetBIOS Node Status -----------------------------------------

    // NBSTAT query for wildcard "*" (NetBIOS node status)
    private static final byte[] NBSTAT_QUERY = {
        0x00, 0x00,  // Transaction ID
        0x00, 0x00,  // Flags
        0x00, 0x01,  // QDCOUNT: 1
        0x00, 0x00,  0x00, 0x00,  0x00, 0x00,  // AN, NS, AR = 0
        // Encoded name: "*" + 14 spaces + null-type, all NetBIOS-encoded
        0x20,
        'C','K','C','A','C','A','C','A','C','A','C','A','C','A','C','A',
        'C','A','C','A','C','A','C','A','C','A','C','A','C','A','A','A',
        0x00,       // end of name
        0x00, 0x21, // QTYPE: NBSTAT
        0x00, 0x01  // QCLASS: IN
    };

    private String resolveNetBIOS(String ip) {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(1000);
            InetAddress target = InetAddress.getByName(ip);
            sock.send(new DatagramPacket(NBSTAT_QUERY, NBSTAT_QUERY.length, target, 137));
            byte[] buf = new byte[1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            return parseNBStatResponse(resp.getData(), resp.getLength());
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseNBStatResponse(byte[] data, int len) {
        // Expected layout: header(12) + question(38) + answer_rr_header(12) = offset 62 for name count
        // The question name is 38 bytes (0x20 + 32 encoded + 0x00 + type(2) + class(2))
        // Answer RR: name(2 compressed) + type(2) + class(2) + ttl(4) + rdlength(2) = 12 bytes
        // Total before rdata: 12 + 38 + 12 = 62
        if (len < 63) return null;
        int nameCount = data[62] & 0xFF;
        int base = 63;
        for (int i = 0; i < nameCount; i++) {
            int off = base + i * 18;
            if (off + 18 > len) break;
            byte type = data[off + 15];
            if (type == 0x00 || type == 0x20) { // workstation or file-server name
                String name = new String(data, off, 15).trim();
                if (!name.isEmpty() && !name.startsWith(" ")) return name;
            }
        }
        return null;
    }

    // ---------- DNS name helpers --------------------------------------------

    private static void skipDnsName(byte[] data, int[] pos) {
        while (pos[0] < data.length) {
            int b = data[pos[0]] & 0xFF;
            if (b == 0) { pos[0]++; return; }
            if ((b & 0xC0) == 0xC0) { pos[0] += 2; return; }
            pos[0] += 1 + b;
        }
    }

    private static String readDnsName(byte[] data, int[] pos, int len) {
        StringBuilder sb = new StringBuilder();
        int savedPos = -1;
        int jumps = 0;
        while (pos[0] < len) {
            int b = data[pos[0]] & 0xFF;
            if (b == 0) { pos[0]++; break; }
            if ((b & 0xC0) == 0xC0) {
                if (pos[0] + 1 >= len) break;
                int ptr = ((b & 0x3F) << 8) | (data[pos[0]+1] & 0xFF);
                if (savedPos < 0) savedPos = pos[0] + 2;
                pos[0] = ptr;
                if (++jumps > 10) break;
                continue;
            }
            pos[0]++;
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < b && pos[0] < len; i++) sb.append((char)(data[pos[0]++] & 0xFF));
        }
        if (savedPos >= 0) pos[0] = savedPos;
        return sb.toString();
    }

    private static String shortName(String name) {
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Sweeps a /24 subnet (e.g. "192.168.1.0/24") in parallel, then reads the
     * ARP table to collect IP→MAC mappings for every host that responded.
     */
    public List<DiscoveredHost> scanSubnet(String cidr) {
        String base = cidrToBase(cidr);
        if (base == null) {
            log.warn("Cannot parse subnet: {}", cidr);
            return List.of();
        }
        log.info("Scanning subnet {} ...", cidr);

        // Ping all 254 hosts in parallel using a bounded thread pool
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {
            final String ip = base + i;
            futures.add(pool.submit(() -> ping(ip, 1000).isReachable() ? ip : null));
        }
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        Set<String> alive = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("Subnet {}: {} hosts responded to ping", cidr, alive.size());

        // Read the ARP table to get MAC addresses for alive hosts
        Map<String, String> arpTable = readArpTable();

        return alive.stream().map(ip -> {
            String mac = arpTable.getOrDefault(ip, "FF:FF:FF:FF:FF:FF");
            return new DiscoveredHost(ip, normaliseMac(mac), null);
        }).collect(Collectors.toList());
    }

    /**
     * Returns the current system ARP table as a map of IP → MAC.
     */
    public Map<String, String> readArpTable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new BufferedReader(new InputStreamReader(proc.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            proc.waitFor(5, TimeUnit.SECONDS);

            Pattern p = IS_WINDOWS ? ARP_WINDOWS : ARP_UNIX;
            Matcher m = p.matcher(output);
            Map<String, String> table = new HashMap<>();
            while (m.find()) table.put(m.group(1), m.group(2));
            return table;
        } catch (Exception e) {
            log.warn("Could not read ARP table: {}", e.getMessage());
            return Map.of();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private int parseLatency(String output) {
        Matcher m = IS_WINDOWS ? LATENCY_WINDOWS.matcher(output) : LATENCY_UNIX.matcher(output);
        if (m.find()) return (int) Double.parseDouble(m.group(1));
        return -1;
    }

    /** Returns the base prefix of a /24 CIDR, e.g. "192.168.1." from "192.168.1.0/24". */
    private String cidrToBase(String cidr) {
        if (cidr == null) return null;
        String ip = cidr.contains("/") ? cidr.split("/")[0] : cidr;
        String[] parts = ip.split("\\.");
        if (parts.length < 3) return null;
        return parts[0] + "." + parts[1] + "." + parts[2] + ".";
    }

    /** Normalises MAC to colon-separated uppercase: a4-2b-8c → A4:2B:8C */
    private String normaliseMac(String mac) {
        if (mac == null) return "FF:FF:FF:FF:FF:FF";
        return mac.replace("-", ":").toUpperCase();
    }

    // -----------------------------------------------------------------------
    // Result types
    // -----------------------------------------------------------------------

    @Data
    @AllArgsConstructor
    public static class PingResult {
        private boolean reachable;
        private int latencyMs;

        public static PingResult unreachable() { return new PingResult(false, 0); }
        public static PingResult ok(int ms)    { return new PingResult(true, ms); }
    }

    @Data
    @AllArgsConstructor
    public static class DiscoveredHost {
        private String ip;
        private String mac;
        private String hostname; // null — resolved lazily if needed
    }
}
