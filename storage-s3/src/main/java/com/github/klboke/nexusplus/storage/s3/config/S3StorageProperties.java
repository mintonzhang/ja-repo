package com.github.klboke.nexusplus.storage.s3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nexus-plus.storage.s3")
public class S3StorageProperties {
  private String name = "default";
  private String engine = "aws-s3";
  private String endpoint = "http://127.0.0.1:9000";
  private String region = "cn-hangzhou";
  private String bucket = "nexus-plus";
  private String keyPrefix = "";
  private String accessKey = "";
  private String secretKey = "";
  private boolean pathStyleAccess = true;
  private int maxConnections = 200;
  private int connectionTimeoutMs = 5000;
  private int socketTimeoutMs = 120000;
  private int connectionAcquisitionTimeoutMs = 10000;
  private boolean tcpKeepAlive = true;
  private long multipartThresholdBytes = 64L * 1024 * 1024;
  private long multipartPartSizeBytes = 16L * 1024 * 1024;
  private int multipartConcurrency = 4;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public boolean isPathStyleAccess() {
    return pathStyleAccess;
  }

  public void setPathStyleAccess(boolean pathStyleAccess) {
    this.pathStyleAccess = pathStyleAccess;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  public int getConnectionTimeoutMs() {
    return connectionTimeoutMs;
  }

  public void setConnectionTimeoutMs(int connectionTimeoutMs) {
    this.connectionTimeoutMs = connectionTimeoutMs;
  }

  public int getSocketTimeoutMs() {
    return socketTimeoutMs;
  }

  public void setSocketTimeoutMs(int socketTimeoutMs) {
    this.socketTimeoutMs = socketTimeoutMs;
  }

  public int getConnectionAcquisitionTimeoutMs() {
    return connectionAcquisitionTimeoutMs;
  }

  public void setConnectionAcquisitionTimeoutMs(int connectionAcquisitionTimeoutMs) {
    this.connectionAcquisitionTimeoutMs = connectionAcquisitionTimeoutMs;
  }

  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  public void setTcpKeepAlive(boolean tcpKeepAlive) {
    this.tcpKeepAlive = tcpKeepAlive;
  }

  public long getMultipartThresholdBytes() {
    return multipartThresholdBytes;
  }

  public void setMultipartThresholdBytes(long multipartThresholdBytes) {
    this.multipartThresholdBytes = multipartThresholdBytes;
  }

  public long getMultipartPartSizeBytes() {
    return multipartPartSizeBytes;
  }

  public void setMultipartPartSizeBytes(long multipartPartSizeBytes) {
    this.multipartPartSizeBytes = multipartPartSizeBytes;
  }

  public int getMultipartConcurrency() {
    return multipartConcurrency;
  }

  public void setMultipartConcurrency(int multipartConcurrency) {
    this.multipartConcurrency = multipartConcurrency;
  }
}
