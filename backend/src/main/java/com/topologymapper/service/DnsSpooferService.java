package com.topologymapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.topologymapper.model.DnsRule;

@Slf4j
@Service
public class DnsSpooferService {

    @Autowired private DnsRuleService ruleService;

    private static final int    DNS_PORT    = 53;
    private static final String UPSTREAM    = "8.8.8.8";
    private static final int    BUFFER_SIZE = 4096;

    private final AtomicBoolean running   = new AtomicBoolean(false);
    private       DatagramSocket socket;
    private       ExecutorService pool;
    private       String          boundIp;

    public boolean isRunning() { return running.get(); }
    public String  getBoundIp() { return boundIp; }

    /** Start the DNS server on the given IP (the hotspot adapter IP). */
    public synchronized String start(String ip) {
        if (running.get()) return "already running on " + boundIp;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            socket  = new DatagramSocket(DNS_PORT, addr);
            pool    = Executors.newFixedThreadPool(8);
            boundIp = ip;
            running.set(true);
            Thread serverThread = new Thread(this::serveLoop, "dns-spoofer");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("DNS spoofer started on {}:{}", ip, DNS_PORT);
            return "started on " + ip + ":" + DNS_PORT;
        } catch (BindException e) {
            return "ERROR: Cannot bind to port 53 on " + ip
                + " — run the backend as Administrator";
        } catch (Exception e) {
            log.error("DNS server start failed: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);
        if (socket != null) socket.close();
        if (pool != null) pool.shutdownNow();
        log.info("DNS spoofer stopped");
    }

    // ---- Main serve loop ---------------------------------------------------

    private void serveLoop() {
        while (running.get()) {
            try {
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                pool.submit(() -> handleQuery(data, pkt.getAddress(), pkt.getPort()));
            } catch (Exception e) {
                if (running.get()) log.debug("DNS receive error: {}", e.getMessage());
            }
        }
    }

    private void handleQuery(byte[] raw, InetAddress clientAddr, int clientPort) {
        try {
            Message query = new Message(raw);
            Record  question = query.getQuestion();
            if (question == null) return;

            String qname = question.getName().toString(true).toLowerCase(); // strip trailing dot
            int    qtype = question.getType();
            log.debug("DNS query from {}: {} (type {})", clientAddr, qname, Type.string(qtype));

            Message response = buildResponse(query, question, qname, qtype);
            if (response == null) return;

            byte[] responseBytes = response.toWire();
            DatagramPacket reply = new DatagramPacket(
                responseBytes, responseBytes.length, clientAddr, clientPort);
            socket.send(reply);
        } catch (Exception e) {
            log.debug("Error handling DNS query: {}", e.getMessage());
        }
    }

    private Message buildResponse(Message query, Record question, String qname, int qtype)
            throws Exception {
        // Only spoof A (IPv4) records — AAAA/MX/etc. are forwarded as-is
        if (qtype == Type.A || qtype == Type.ANY) {
            String targetIp = ruleService.resolveRule(qname);
            if (targetIp != null) {
                log.info("DNS spoof: {} → {}", qname, targetIp);
                return buildSpoofedResponse(query, question, qname, targetIp);
            }
        }
        // Forward to upstream
        return forwardUpstream(query);
    }

    private Message buildSpoofedResponse(Message query, Record question, String qname, String ip)
            throws Exception {
        Message resp = new Message(query.getHeader().getID());
        resp.getHeader().setFlag(Flags.QR);
        resp.getHeader().setFlag(Flags.AA);
        resp.getHeader().setFlag(Flags.RA);
        resp.addRecord(question, Section.QUESTION);

        Name dnsName = Name.fromString(qname + ".");
        ARecord answer = new ARecord(dnsName, DClass.IN, 60L,
            InetAddress.getByName(ip));
        resp.addRecord(answer, Section.ANSWER);
        return resp;
    }

    private Message forwardUpstream(Message query) {
        try {
            SimpleResolver resolver = new SimpleResolver(UPSTREAM);
            resolver.setTimeout(Duration.ofSeconds(4));
            return resolver.send(query);
        } catch (Exception e) {
            log.debug("Upstream DNS failed: {}", e.getMessage());
            return null;
        }
    }

    /** Returns a list of candidate hotspot interface IPs (private-range, non-loopback). */
    public static List<Map<String, String>> listCandidateInterfaces() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (!(addr instanceof Inet4Address)) continue;
                    String ip = addr.getHostAddress();
                    if (isPrivate(ip)) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("name", iface.getDisplayName());
                        entry.put("ip", ip);
                        result.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Interface list failed: {}", e.getMessage());
        }
        return result;
    }

    private static boolean isPrivate(String ip) {
        return ip.startsWith("192.168.") || ip.startsWith("10.")
            || (ip.startsWith("172.") && isPrivate172(ip));
    }

    private static boolean isPrivate172(String ip) {
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) { return false; }
    }
}
