package com.topologymapper.service;

import com.topologymapper.model.*;
import com.topologymapper.store.TopologyStore;
import com.topologymapper.websocket.WebSocketBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class SecurityScanService {

    @Autowired private PortScanService          portScanner;
    @Autowired private CveService               cveService;
    @Autowired private DefaultCredentialService credService;
    @Autowired private ArpMonitorService        arpMonitor;
    @Autowired private TopologyStore            store;
    @Autowired private WebSocketBroadcaster     broadcaster;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Runs the full security scan pipeline for a device asynchronously.
     * Called automatically after device discovery and on manual request.
     */
    @Async
    public void scanAsync(Device device) {
        log.info("Security scan starting on {} ({})", device.getHostname(), device.getIp());
        try {
            SecurityScanResult result = performScan(device);
            device.setSecurityScan(result);
            store.putDevice(device);
            broadcaster.broadcastFullState();
            log.info("Security scan complete on {} — risk: {}, {} open ports, {} CVEs, {} cred findings",
                device.getHostname(), result.getRiskLevel(),
                result.getOpenPortCount(), result.getCveCount(), result.getVulnCount());
        } catch (Exception e) {
            log.error("Security scan failed for {}: {}", device.getHostname(), e.getMessage());
        }
    }

    public SecurityScanResult performScan(Device device) {
        String ip = device.getIp();

        // 1. Port scan
        List<OpenPort> openPorts = portScanner.scan(ip);

        // 2. CVE lookup (only for ports we found open)
        List<CveMatch> cves = openPorts.isEmpty()
            ? List.of()
            : cveService.lookupForPorts(openPorts);

        // 3. Default credential testing
        List<CredentialFinding> creds = openPorts.isEmpty()
            ? List.of()
            : credService.test(ip, openPorts);

        // 4. ARP anomaly check
        List<String> arpAnomalies = arpMonitor.checkDevice(ip, device.getMac());

        // 5. Compute overall risk level
        String riskLevel = computeRisk(cves, creds, arpAnomalies, openPorts);

        return SecurityScanResult.builder()
            .timestamp(LocalTime.now().format(TIME_FMT))
            .riskLevel(riskLevel)
            .openPorts(openPorts)
            .cveMatches(cves)
            .credentialFindings(creds)
            .arpAnomalies(arpAnomalies)
            .build();
    }

    private String computeRisk(List<CveMatch> cves, List<CredentialFinding> creds,
                                List<String> arpAnomalies, List<OpenPort> openPorts) {
        // CRITICAL: CVSS >= 9.0 OR successful SSH/Telnet credential login
        boolean critCve  = cves.stream().anyMatch(c -> c.getCvssScore() >= 9.0);
        boolean critCred = creds.stream().anyMatch(c -> c.isVulnerable()
            && ("SSH".equals(c.getService()) || "Telnet".equals(c.getService())));
        if (critCve || critCred) return "CRITICAL";

        // HIGH: CVSS >= 7.0 OR successful HTTP credential login OR ARP anomaly
        boolean highCve  = cves.stream().anyMatch(c -> c.getCvssScore() >= 7.0);
        boolean highCred = creds.stream().anyMatch(CredentialFinding::isVulnerable);
        boolean arpIssue = !arpAnomalies.isEmpty();
        if (highCve || highCred || arpIssue) return "HIGH";

        // MEDIUM: CVSS >= 4.0 OR risky open ports with no cred finding
        boolean medCve   = cves.stream().anyMatch(c -> c.getCvssScore() >= 4.0);
        boolean riskyPort = openPorts.stream().anyMatch(p ->
            List.of("Telnet","RDP","FTP","VNC","MQTT","RTSP").contains(p.getService()));
        if (medCve || riskyPort) return "MEDIUM";

        // LOW: any open ports found
        if (!openPorts.isEmpty()) return "LOW";

        return "CLEAN";
    }
}
