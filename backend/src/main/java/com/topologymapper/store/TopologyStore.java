package com.topologymapper.store;

import com.topologymapper.model.*;
import com.topologymapper.persistence.PersistenceService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class TopologyStore {

    @Autowired private PersistenceService persistenceService;

    private final ConcurrentHashMap<String, Device> devices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Link>   links   = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Alert>        alerts  = new CopyOnWriteArrayList<>();

    private final AtomicLong deviceCounter = new AtomicLong(100);
    private final AtomicLong linkCounter   = new AtomicLong(10);
    private final AtomicLong alertCounter  = new AtomicLong(10);

    @PostConstruct
    public void loadFromDatabase() {
        PersistenceService.LoadResult result = persistenceService.loadAll();
        result.devices().forEach(d -> {
            devices.put(d.getId(), d);
            syncCounter(deviceCounter, d.getId().replaceAll("[^0-9]", ""));
        });
        result.links().forEach(l -> {
            links.put(l.getId(), l);
            syncCounter(linkCounter, l.getId().replaceAll("[^0-9]", ""));
        });
    }

    private void syncCounter(AtomicLong counter, String numStr) {
        if (numStr.isEmpty()) return;
        try { counter.accumulateAndGet(Long.parseLong(numStr), Math::max); }
        catch (NumberFormatException ignored) {}
    }

    // --- Device ---

    public void putDevice(Device d) {
        devices.put(d.getId(), d);
        persistenceService.saveDevice(d);
    }

    public Optional<Device> getDevice(String id) { return Optional.ofNullable(devices.get(id)); }

    public Collection<Device> allDevices() { return devices.values(); }

    public boolean removeDevice(String id) {
        boolean removed = devices.remove(id) != null;
        if (removed) persistenceService.deleteDevice(id);
        return removed;
    }

    public boolean containsDevice(String id) { return devices.containsKey(id); }

    // --- Link ---

    public void putLink(Link l) {
        links.put(l.getId(), l);
        persistenceService.saveLink(l);
    }

    public Collection<Link> allLinks() { return links.values(); }

    public Optional<Link> getLink(String id) { return Optional.ofNullable(links.get(id)); }

    public boolean removeLinkById(String id) {
        boolean removed = links.remove(id) != null;
        if (removed) persistenceService.deleteLink(id);
        return removed;
    }

    public void clearAll() {
        persistenceService.clearAll();
        devices.clear();
        links.clear();
        alerts.clear();
    }

    public void removeLinksForDevice(String deviceId) {
        links.values().removeIf(l ->
                l.getSource().equals(deviceId) || l.getTarget().equals(deviceId));
        persistenceService.deleteLinksForDevice(deviceId);
    }

    public List<Link> linksForDevice(String deviceId) {
        return links.values().stream()
                .filter(l -> l.getSource().equals(deviceId) || l.getTarget().equals(deviceId))
                .collect(Collectors.toList());
    }

    // --- Alert ---

    public void addAlert(Alert a) {
        alerts.add(0, a);
        if (alerts.size() > 50) alerts.remove(alerts.size() - 1);
    }

    public List<Alert> recentAlerts(int limit) {
        return alerts.subList(0, Math.min(limit, alerts.size()));
    }

    // --- ID generators ---

    public String nextDeviceId(DeviceType type) {
        long n = deviceCounter.incrementAndGet();
        return switch (type) {
            case ROUTER -> "gw-"   + n;
            case MOBILE -> "mob-"  + n;
            case HOST   -> "host-" + n;
        };
    }

    public String nextLinkId()  { return "l" + linkCounter.incrementAndGet(); }
    public String nextAlertId() { return "a" + alertCounter.incrementAndGet(); }
}
