package com.topologymapper.controller;

import com.topologymapper.model.DnsRule;
import com.topologymapper.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dns")
public class DnsController {

    @Autowired private DnsSpooferService   spoofer;
    @Autowired private CaptivePortalService portal;
    @Autowired private DnsRuleService       ruleService;
    @Autowired private CaService            caService;

    // --- Status / control ---------------------------------------------------

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dnsRunning",    spoofer.isRunning());
        m.put("portalRunning", portal.isRunning());
        m.put("boundIp",       spoofer.getBoundIp());
        m.put("ruleCount",     ruleService.getAllRules().size());
        m.put("interfaces",    DnsSpooferService.listCandidateInterfaces());
        return m;
    }

    @PostMapping("/start")
    public Map<String, String> start(@RequestBody(required = false) Map<String, String> body) {
        String ip = (body != null && body.containsKey("ip")) ? body.get("ip") : autoDetectIp();
        String dnsMsg    = spoofer.start(ip);
        String portalMsg = portal.start(ip);
        return Map.of("dns", dnsMsg, "portal", portalMsg, "ip", ip);
    }

    @PostMapping("/stop")
    public Map<String, String> stop() {
        spoofer.stop();
        portal.stop();
        return Map.of("status", "stopped");
    }

    // --- Rules CRUD ---------------------------------------------------------

    @GetMapping("/rules")
    public List<DnsRule> getRules() {
        return ruleService.getAllRules();
    }

    @PostMapping("/rules")
    public DnsRule addRule(@RequestBody DnsRule rule) {
        return ruleService.addRule(rule);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable String id) {
        ruleService.deleteRule(id);
    }

    @PatchMapping("/rules/{id}/toggle")
    public Map<String, Object> toggleRule(@PathVariable String id) {
        boolean ok = ruleService.toggleRule(id);
        return Map.of("ok", ok);
    }

    // --- CA cert download ---------------------------------------------------

    @GetMapping(value = "/ca.crt", produces = "application/x-x509-ca-cert")
    public ResponseEntity<byte[]> downloadCa() throws Exception {
        byte[] der = caService.getCaCertDer();
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=topology-mapper-ca.crt")
            .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
            .body(der);
    }

    // --- Helpers ------------------------------------------------------------

    private String autoDetectIp() {
        // Prefer 192.168.x.x (typical hotspot range), fall back to first candidate
        return DnsSpooferService.listCandidateInterfaces().stream()
            .filter(m -> m.get("ip").startsWith("192.168."))
            .map(m -> m.get("ip"))
            .findFirst()
            .orElseGet(() ->
                DnsSpooferService.listCandidateInterfaces().stream()
                    .map(m -> m.get("ip"))
                    .findFirst()
                    .orElse("127.0.0.1"));
    }
}
