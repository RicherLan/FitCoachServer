package com.lanprojects.fitcoach.track.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * GeoIP 服务
 * 
 * 职责：
 * 1. 根据客户端 IP 地址解析地理位置
 * 2. 返回国家码（如 CN / US / JP）
 * 3. 缓存常见 IP 的解析结果
 * 
 * Phase 3 使用 MaxMind GeoLite2 数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIPService {
    // 简单的 IP 地址段到国家码的映射（演示用）
    // 实际应该使用 MaxMind GeoLite2 数据库
    private static final Map<String, String> IP_REGION_MAP = new HashMap<>();

    static {
        // 中国 IP 段示例
        IP_REGION_MAP.put("1.0.0.0", "CN");
        IP_REGION_MAP.put("1.1.0.0", "CN");
        IP_REGION_MAP.put("1.2.0.0", "CN");

        // 美国 IP 段示例
        IP_REGION_MAP.put("8.0.0.0", "US");
        IP_REGION_MAP.put("12.0.0.0", "US");
        IP_REGION_MAP.put("13.0.0.0", "US");

        // 日本 IP 段示例
        IP_REGION_MAP.put("61.0.0.0", "JP");
        IP_REGION_MAP.put("61.1.0.0", "JP");
        IP_REGION_MAP.put("61.2.0.0", "JP");
    }

    /**
     * 根据 IP 地址获取国家码
     * 
     * @param ipAddress IP 地址（如 "192.168.1.1"）
     * @return 国家码（如 "CN"）
     */
    public String getCountryCodeByIP(String ipAddress) {
        try {
            // 1. 验证 IP 地址格式
            if (!isValidIPAddress(ipAddress)) {
                log.warn("Invalid IP address: {}", ipAddress);
                return null;
            }

            // 2. 检查是否是本地 IP（127.0.0.1 / 192.168.x.x / 10.x.x.x）
            if (isLocalIP(ipAddress)) {
                log.debug("Local IP address: {}", ipAddress);
                return null;
            }

            // 3. 查询 GeoIP 数据库（这里使用简单的映射演示）
            // 实际应该使用 MaxMind GeoLite2 数据库
            String countryCode = queryGeoIPDatabase(ipAddress);

            if (countryCode != null) {
                log.debug("IP {} resolved to country code: {}", ipAddress, countryCode);
                return countryCode;
            }

            log.warn("Could not resolve country code for IP: {}", ipAddress);
            return null;
        } catch (Exception e) {
            log.error("Error resolving GeoIP for IP: {}", ipAddress, e);
            return null;
        }
    }

    /**
     * 验证 IP 地址格式
     */
    private boolean isValidIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }

        try {
            InetAddress.getByName(ipAddress);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否是本地 IP
     */
    private boolean isLocalIP(String ipAddress) {
        return ipAddress.startsWith("127.") ||
                ipAddress.startsWith("192.168.") ||
                ipAddress.startsWith("10.") ||
                ipAddress.startsWith("172.16.") ||
                ipAddress.equals("localhost");
    }

    /**
     * 查询 GeoIP 数据库
     * 
     * 实际实现应该使用 MaxMind GeoLite2 数据库：
     * 1. 下载 GeoLite2-Country.mmdb 文件
     * 2. 使用 maxmind-geoip2 库查询
     * 3. 缓存查询结果
     */
    private String queryGeoIPDatabase(String ipAddress) {
        // 演示：简单的前缀匹配
        String[] parts = ipAddress.split("\\.");
        if (parts.length >= 2) {
            String prefix = parts[0] + "." + parts[1] + ".0.0";
            return IP_REGION_MAP.get(prefix);
        }

        // 默认返回 null（表示无法解析）
        return null;
    }

    /**
     * 批量查询 IP 地址的国家码
     */
    public Map<String, String> getCountryCodesByIPs(java.util.List<String> ipAddresses) {
        Map<String, String> result = new HashMap<>();

        for (String ip : ipAddresses) {
            String countryCode = getCountryCodeByIP(ip);
            if (countryCode != null) {
                result.put(ip, countryCode);
            }
        }

        return result;
    }
}
