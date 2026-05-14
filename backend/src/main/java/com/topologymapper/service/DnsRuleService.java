package com.topologymapper.service;

import com.topologymapper.model.DnsRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class DnsRuleService {

    private final Map<String, DnsRule> rules   = new ConcurrentHashMap<>();
    private final AtomicInteger        counter = new AtomicInteger(1);

    public List<DnsRule> getAllRules() {
        return new ArrayList<>(rules.values());
    }

    public DnsRule addRule(DnsRule rule) {
        rule.setId("rule-" + counter.getAndIncrement());
        if (rule.getTargetIp() == null || rule.getTargetIp().isBlank())
            rule.setTargetIp("127.0.0.1");
        if (rule.getPageTitle() == null || rule.getPageTitle().isBlank())
            rule.setPageTitle("DNS Redirect");
        if (rule.getPageBody() == null || rule.getPageBody().isBlank())
            rule.setPageBody("This domain has been redirected by your network administrator.");
        rule.setEnabled(true);
        rule.setCatchAll("*".equals(rule.getDomain()));
        rules.put(rule.getId(), rule);
        log.info("DNS rule added: {} → {}", rule.getDomain(), rule.getTargetIp());
        return rule;
    }

    public boolean deleteRule(String id) {
        return rules.remove(id) != null;
    }

    public boolean toggleRule(String id) {
        DnsRule rule = rules.get(id);
        if (rule == null) return false;
        rule.setEnabled(!rule.isEnabled());
        return true;
    }

    /**
     * Returns the target IP for a DNS query, or null if it should be forwarded upstream.
     * Matching order: exact match → wildcard subdomain → catch-all.
     */
    public String resolveRule(String hostname) {
        DnsRule catchAll = null;

        for (DnsRule rule : rules.values()) {
            if (!rule.isEnabled()) continue;
            String domain = rule.getDomain().toLowerCase();

            if (rule.isCatchAll()) {
                catchAll = rule;
                continue;
            }
            // Exact match
            if (hostname.equals(domain)) return rule.getTargetIp();
            // Wildcard: *.henry.com matches sub.henry.com
            if (domain.startsWith("*.")) {
                String base = domain.substring(2);
                if (hostname.endsWith("." + base) || hostname.equals(base))
                    return rule.getTargetIp();
            }
        }

        return catchAll != null ? catchAll.getTargetIp() : null;
    }

    /** Returns the DnsRule that governs this hostname (for landing page content). */
    public DnsRule findMatchingRule(String hostname) {
        DnsRule catchAll = null;
        for (DnsRule rule : rules.values()) {
            if (!rule.isEnabled()) continue;
            String domain = rule.getDomain().toLowerCase();
            if (rule.isCatchAll()) { catchAll = rule; continue; }
            if (hostname.equals(domain)) return rule;
            if (domain.startsWith("*.")) {
                String base = domain.substring(2);
                if (hostname.endsWith("." + base) || hostname.equals(base)) return rule;
            }
        }
        return catchAll;
    }
}
