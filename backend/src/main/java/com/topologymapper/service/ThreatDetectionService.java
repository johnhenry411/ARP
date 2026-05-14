package com.topologymapper.service;

import com.topologymapper.model.Device;
import com.topologymapper.model.DeviceStatus;
import com.topologymapper.model.ThreatLevel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ThreatDetectionService {

    public record ThreatResult(ThreatLevel level, String reason) {
        public static ThreatResult none() { return new ThreatResult(ThreatLevel.none, null); }
    }

    public ThreatResult evaluate(Device device) {
        List<Integer> history = device.getBandwidthHistory();

        if (device.getStatus() == DeviceStatus.NEW && "Unknown".equals(device.getVendor())) {
            return new ThreatResult(ThreatLevel.high,
                    "New device with unrecognised vendor joined the network");
        }

        if (history != null && history.stream().anyMatch(v -> v > 50_000)) {
            int peak = history.stream().mapToInt(Integer::intValue).max().orElse(0);
            return new ThreatResult(ThreatLevel.high,
                    "Extreme bandwidth: " + peak + " Kbps — possible data exfiltration");
        }

        if ("Unknown".equals(device.getVendor()) && !device.isGateway()) {
            return new ThreatResult(ThreatLevel.low,
                    "Unrecognised vendor — hardware not identified via MAC lookup");
        }

        if (history != null && history.size() >= 3) {
            int latest = history.get(history.size() - 1);
            double avg = history.stream().mapToInt(Integer::intValue).average().orElse(0);

            if (avg > 0 && latest > avg * 3 && latest > 1_000) {
                int times = (int) Math.round(latest / avg);
                return new ThreatResult(ThreatLevel.low,
                        "Bandwidth spike: " + latest + " Kbps (~" + times + "× normal)");
            }

            if (avg > 5_000) {
                return new ThreatResult(ThreatLevel.low,
                        "Sustained high usage: avg " + (int) avg + " Kbps over last readings");
            }
        }

        return ThreatResult.none();
    }
}
