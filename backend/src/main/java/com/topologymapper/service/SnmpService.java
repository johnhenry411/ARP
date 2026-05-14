package com.topologymapper.service;

import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queries SNMP-enabled devices (typically routers) for:
 * - Bandwidth: sum of ifHCInOctets + ifHCOutOctets across all interfaces
 * - Connected devices: the router's ARP table via ipNetToMediaPhysAddress
 *
 * Only activates when a device has snmpCommunity set.
 */
@Slf4j
@Service
public class SnmpService {

    // OIDs
    private static final String OID_SYS_DESCR       = "1.3.6.1.2.1.1.1.0";
    private static final String OID_IF_HC_IN_OCTETS  = "1.3.6.1.2.1.31.1.1.1.6";   // ifHCInOctets table
    private static final String OID_IF_HC_OUT_OCTETS = "1.3.6.1.2.1.31.1.1.1.10";  // ifHCOutOctets table
    private static final String OID_ARP_MAC          = "1.3.6.1.2.1.4.22.1.2";      // ipNetToMediaPhysAddress

    // Per-device previous octet counters for delta bandwidth calculation
    private final ConcurrentHashMap<String, long[]> prevOctets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>   prevTime   = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Returns true if the device responds to an SNMP sysDescr GET. */
    public boolean isReachable(String ip, String community, int port) {
        try (Snmp snmp = newSnmp()) {
            VariableBinding vb = get(snmp, target(ip, port, community), OID_SYS_DESCR);
            return vb != null && !(vb.getVariable() instanceof Null);
        } catch (Exception e) {
            log.debug("SNMP not reachable at {}:{} – {}", ip, port, e.getMessage());
            return false;
        }
    }

    /**
     * Measures current bandwidth in Mbps by computing the delta of
     * ifHCInOctets + ifHCOutOctets since the last call for this device id.
     * Returns 0 on first call (no previous sample yet).
     */
    public int getBandwidthKbps(String deviceId, String ip, String community, int port) {
        try (Snmp snmp = newSnmp()) {
            CommunityTarget<UdpAddress> tgt = target(ip, port, community);

            // Walk both counter tables and sum across all interfaces
            long totalIn  = walkSumCounters(snmp, tgt, OID_IF_HC_IN_OCTETS);
            long totalOut = walkSumCounters(snmp, tgt, OID_IF_HC_OUT_OCTETS);
            long totalOctets = totalIn + totalOut;
            long now = System.currentTimeMillis();

            long[] prev    = prevOctets.get(deviceId);
            Long   prevTs  = prevTime.get(deviceId);

            prevOctets.put(deviceId, new long[]{totalOctets});
            prevTime.put(deviceId, now);

            if (prev == null || prevTs == null) return 0; // first sample

            long deltaOctets = totalOctets - prev[0];
            long deltaMs     = now - prevTs;
            if (deltaMs <= 0 || deltaOctets < 0) return 0;

            // bytes/ms → bits/ms * 1000 → bits/s → Kbps
            return (int) Math.min(10_000_000, (deltaOctets * 8.0 / deltaMs));
        } catch (Exception e) {
            log.debug("SNMP bandwidth query failed for {}: {}", ip, e.getMessage());
            return -1; // -1 signals "SNMP failed, fall back to previous value"
        }
    }

    /**
     * Reads the router's ARP table via SNMP (ipNetToMediaPhysAddress).
     * Returns a map of IP → MAC for every client the router knows about.
     */
    public Map<String, String> getConnectedDevices(String ip, String community, int port) {
        Map<String, String> result = new LinkedHashMap<>();
        try (Snmp snmp = newSnmp()) {
            CommunityTarget<UdpAddress> tgt = target(ip, port, community);
            List<VariableBinding> rows = walk(snmp, tgt, OID_ARP_MAC);
            for (VariableBinding vb : rows) {
                // OID suffix encodes the IP: .ifIndex.a.b.c.d
                String oid    = vb.getOid().toString();
                String suffix = oid.substring(OID_ARP_MAC.length() + 1); // remove base + dot
                String[] parts = suffix.split("\\.");
                if (parts.length < 5) continue;
                // last 4 octets = IP address
                String clientIp = parts[parts.length - 4] + "." + parts[parts.length - 3]
                        + "." + parts[parts.length - 2] + "." + parts[parts.length - 1];
                // value = MAC as OctetString bytes
                String mac = formatMac(vb.getVariable().toString());
                result.put(clientIp, mac);
            }
        } catch (Exception e) {
            log.debug("SNMP ARP table query failed for {}: {}", ip, e.getMessage());
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Snmp newSnmp() throws IOException {
        DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();
        return snmp;
    }

    private CommunityTarget<UdpAddress> target(String ip, int port, String community) {
        CommunityTarget<UdpAddress> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(community));
        target.setAddress(new UdpAddress(ip + "/" + port));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(3000);
        target.setRetries(1);
        return target;
    }

    private VariableBinding get(Snmp snmp, CommunityTarget<UdpAddress> target, String oidStr) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oidStr)));
        pdu.setType(PDU.GET);
        ResponseEvent<?> resp = snmp.send(pdu, target);
        if (resp == null || resp.getResponse() == null) return null;
        return resp.getResponse().getVariableBindings().isEmpty()
                ? null : resp.getResponse().get(0);
    }

    /** GETNEXT walk — collects all rows whose OID starts with baseOid. */
    private List<VariableBinding> walk(Snmp snmp, CommunityTarget<UdpAddress> target, String baseOid)
            throws IOException {
        List<VariableBinding> results = new ArrayList<>();
        OID currentOid = new OID(baseOid);

        for (int i = 0; i < 512; i++) { // safety limit
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(currentOid));
            pdu.setType(PDU.GETNEXT);
            ResponseEvent<?> resp = snmp.send(pdu, target);
            if (resp == null || resp.getResponse() == null) break;

            VariableBinding vb = resp.getResponse().get(0);
            if (vb.getVariable() instanceof Null) break;
            if (!vb.getOid().startsWith(new OID(baseOid))) break;

            results.add(vb);
            currentOid = vb.getOid();
        }
        return results;
    }

    /** Sum all counter values from a GETNEXT walk of a counter table. */
    private long walkSumCounters(Snmp snmp, CommunityTarget<UdpAddress> target, String baseOid)
            throws IOException {
        return walk(snmp, target, baseOid).stream()
                .mapToLong(vb -> {
                    try { return vb.getVariable().toLong(); }
                    catch (Exception e) { return 0L; }
                }).sum();
    }

    /** Formats a MAC from SNMP OctetString hex representation. */
    private String formatMac(String raw) {
        // snmp4j renders OctetString as "a4:2b:8c:11:ff:02" or hex bytes
        if (raw == null || raw.isEmpty()) return "FF:FF:FF:FF:FF:FF";
        return raw.toUpperCase().replace("-", ":");
    }
}
