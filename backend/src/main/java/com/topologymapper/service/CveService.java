package com.topologymapper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topologymapper.model.CveMatch;
import com.topologymapper.model.OpenPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CveService {

    @Autowired private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    private record CacheEntry(List<CveMatch> matches, long expiry) {}
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 60 * 1_000L;

    // Maps port/service name to the NVD keyword most likely to return useful CVEs
    private static final Map<String, String> KEYWORD_MAP = Map.ofEntries(
        Map.entry("SSH",           "OpenSSH"),
        Map.entry("HTTP",          "Apache HTTP Server"),
        Map.entry("HTTP-alt",      "Apache HTTP Server"),
        Map.entry("FTP",           "ProFTPD vsftpd"),
        Map.entry("SMB",           "Windows SMBv1 EternalBlue"),
        Map.entry("RDP",           "Windows Remote Desktop"),
        Map.entry("Telnet",        "Telnet daemon"),
        Map.entry("MySQL",         "MySQL Server"),
        Map.entry("PostgreSQL",    "PostgreSQL"),
        Map.entry("Redis",         "Redis unauthenticated"),
        Map.entry("MongoDB",       "MongoDB unauthenticated"),
        Map.entry("Elasticsearch", "Elasticsearch unauthenticated"),
        Map.entry("VNC",           "RealVNC TigerVNC"),
        Map.entry("MQTT",          "Mosquitto MQTT"),
        Map.entry("RTSP",          "RTSP camera"),
        Map.entry("SMTP",          "Postfix Exim SMTP")
    );

    public List<CveMatch> lookupForPorts(List<OpenPort> openPorts) {
        List<CveMatch> all = new ArrayList<>();
        Set<String> queried = new HashSet<>();
        for (OpenPort port : openPorts) {
            String service = port.getService();
            if (!queried.add(service)) continue; // deduplicate per-service
            all.addAll(lookupForService(service, port.getPort()));
        }
        // Sort by CVSS score descending
        all.sort(Comparator.comparingDouble(CveMatch::getCvssScore).reversed());
        return all;
    }

    private List<CveMatch> lookupForService(String service, int port) {
        String key = service.toLowerCase();
        CacheEntry cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiry()) {
            return tagged(cached.matches(), port, service);
        }
        List<CveMatch> fresh = fetchFromNvd(service, port);
        cache.put(key, new CacheEntry(fresh, System.currentTimeMillis() + CACHE_TTL_MS));
        return fresh;
    }

    private List<CveMatch> fetchFromNvd(String service, int port) {
        String keyword = KEYWORD_MAP.getOrDefault(service, service);
        String url = "https://services.nvd.nist.gov/rest/json/cves/2.0?keywordSearch="
            + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
            + "&resultsPerPage=5";
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) return List.of();
            return parseResponse(body, port, service);
        } catch (Exception e) {
            log.debug("CVE lookup failed for service '{}': {}", service, e.getMessage());
            return List.of();
        }
    }

    private List<CveMatch> parseResponse(String json, int port, String service) {
        List<CveMatch> results = new ArrayList<>();
        try {
            JsonNode root  = objectMapper.readTree(json);
            JsonNode vulns = root.path("vulnerabilities");
            for (JsonNode entry : vulns) {
                JsonNode cve = entry.path("cve");
                String id = cve.path("id").asText();

                // Pick best available CVSS score (v3.1 preferred, fall back to v3.0, then v2)
                double score = 0;
                String severity = "UNKNOWN";
                for (String key : new String[]{"cvssMetricV31","cvssMetricV30","cvssMetricV2"}) {
                    JsonNode metrics = cve.path("metrics").path(key);
                    if (metrics.isArray() && !metrics.isEmpty()) {
                        JsonNode data = metrics.get(0).path("cvssData");
                        score    = data.path("baseScore").asDouble(0);
                        severity = data.path("baseSeverity").asText(
                                   data.path("baseSeverity").asText("UNKNOWN"));
                        break;
                    }
                }
                if (score < 4.0) continue; // skip low/informational

                String desc = "";
                for (JsonNode d : cve.path("descriptions")) {
                    if ("en".equals(d.path("lang").asText())) {
                        desc = d.path("value").asText();
                        if (desc.length() > 160) desc = desc.substring(0, 157) + "…";
                        break;
                    }
                }

                results.add(CveMatch.builder()
                    .cveId(id).cvssScore(score).severity(severity).description(desc)
                    .affectedPort(port + " (" + service + ")")
                    .build());
            }
        } catch (Exception e) {
            log.debug("Failed to parse NVD response: {}", e.getMessage());
        }
        return results;
    }

    // Re-tag already-cached results with the current port/service label
    private List<CveMatch> tagged(List<CveMatch> matches, int port, String service) {
        String tag = port + " (" + service + ")";
        return matches.stream()
            .map(m -> CveMatch.builder()
                .cveId(m.getCveId()).cvssScore(m.getCvssScore())
                .severity(m.getSeverity()).description(m.getDescription())
                .affectedPort(tag).build())
            .toList();
    }
}
