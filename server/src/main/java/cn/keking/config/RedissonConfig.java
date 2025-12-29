package cn.keking.config;

import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * Redisson 客户端配置
 * Created by kl on 2017/09/26.
 */
@ConditionalOnExpression("'${cache.type:default}'.equals('redis')")
@ConfigurationProperties(prefix = "spring.redisson")
@Configuration
public class RedissonConfig {

    // ========================== 连接配置 ==========================
    private static String address;
    private static String password;
    private static String clientName;
    private static int database = 0;
    private static String mode = "single";
    private static String masterName = "kkfile";

    // ========================== 超时配置 ==========================
    private static int idleConnectionTimeout = 10000;
    private static int connectTimeout = 10000;
    private static int timeout = 3000;

    // ========================== 重试配置 ==========================
    private static int retryAttempts = 3;
    private static int retryInterval = 1500;

    // ========================== 连接池配置 ==========================
    private static int connectionMinimumIdleSize = 10;
    private static int connectionPoolSize = 64;
    private static int subscriptionsPerConnection = 5;
    private static int subscriptionConnectionMinimumIdleSize = 1;
    private static int subscriptionConnectionPoolSize = 50;

    // ========================== 其他配置 ==========================
    private static int dnsMonitoringInterval = 5000;
    private static int thread; // 当前处理核数量 * 2
    private static String codec = "org.redisson.codec.JsonJacksonCodec";

    @Bean
    public static RedissonClient config() throws Exception {
        Config config = new Config();

        // 密码处理
        if (StringUtils.isBlank(password)) {
            password = null;
        }

        // 根据模式创建对应的 Redisson 配置
        switch (mode) {
            case "cluster":
                configureClusterMode(config);
                break;
            case "master-slave":
                configureMasterSlaveMode(config);
                break;
            case "sentinel":
                configureSentinelMode(config);
                break;
            default:
                configureSingleMode(config);
                break;
        }

        return Redisson.create(config);
    }

    // ========================== 配置方法 ==========================

    /**
     * 配置集群模式
     */
    private static void configureClusterMode(Config config) {
        String[] clusterAddresses = address.split(",");
        config.useClusterServers()
                .setScanInterval(2000)
                .addNodeAddress(clusterAddresses)
                .setPassword(password)
                .setRetryAttempts(retryAttempts)
                .setTimeout(timeout)
                .setMasterConnectionPoolSize(100)
                .setSlaveConnectionPoolSize(100);
    }

    /**
     * 配置主从模式
     */
    private static void configureMasterSlaveMode(Config config) {
        String[] masterSlaveAddresses = address.split(",");
        validateMasterSlaveAddresses(masterSlaveAddresses);

        String[] slaveAddresses = new String[masterSlaveAddresses.length - 1];
        System.arraycopy(masterSlaveAddresses, 1, slaveAddresses, 0, slaveAddresses.length);

        config.useMasterSlaveServers()
                .setDatabase(database)
                .setPassword(password)
                .setMasterAddress(masterSlaveAddresses[0])
                .addSlaveAddress(slaveAddresses);
    }

    /**
     * 配置哨兵模式
     */
    private static void configureSentinelMode(Config config) {
        String[] sentinelAddresses = address.split(",");
        config.useSentinelServers()
                .setDatabase(database)
                .setPassword(password)
                .setMasterName(masterName)
                .addSentinelAddress(sentinelAddresses);
    }

    /**
     * 配置单机模式
     */
    private static void configureSingleMode(Config config) throws Exception {
        config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                .setConnectionPoolSize(connectionPoolSize)
                .setDatabase(database)
                .setDnsMonitoringInterval(dnsMonitoringInterval)
                .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                .setSubscriptionsPerConnection(subscriptionsPerConnection)
                .setClientName(clientName)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval)
                .setTimeout(timeout)
                .setConnectTimeout(connectTimeout)
                .setIdleConnectionTimeout(idleConnectionTimeout)
                .setPassword(StringUtils.trimToNull(password));

        // 设置编码器
        Class<?> codecClass = ClassUtils.forName(getCodec(), ClassUtils.getDefaultClassLoader());
        Codec codecInstance = (Codec) codecClass.getDeclaredConstructor().newInstance();
        config.setCodec(codecInstance);
        // 设置线程和事件循环组
        config.setThreads(thread);
        config.setEventLoopGroup(new NioEventLoopGroup());
    }

    /**
     * 验证主从模式地址
     */
    private static void validateMasterSlaveAddresses(String[] addresses) {
        if (addresses.length == 1) {
            throw new IllegalArgumentException(
                    "redis.redisson.address MUST have multiple redis addresses for master-slave mode.");
        }
    }

    // ========================== Getter和Setter方法 ==========================

    // 连接配置
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        RedissonConfig.address = address;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        RedissonConfig.password = password;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        RedissonConfig.clientName = clientName;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        RedissonConfig.database = database;
    }

    public static String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        RedissonConfig.mode = mode;
    }

    public static String getMasterNamee() {
        return masterName;
    }

    public void setMasterNamee(String masterName) {
        RedissonConfig.masterName = masterName;
    }

    // 超时配置
    public int getIdleConnectionTimeout() {
        return idleConnectionTimeout;
    }

    public void setIdleConnectionTimeout(int idleConnectionTimeout) {
        RedissonConfig.idleConnectionTimeout = idleConnectionTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        RedissonConfig.connectTimeout = connectTimeout;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        RedissonConfig.timeout = timeout;
    }

    // 重试配置
    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        RedissonConfig.retryAttempts = retryAttempts;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(int retryInterval) {
        RedissonConfig.retryInterval = retryInterval;
    }

    // 连接池配置
    public int getConnectionMinimumIdleSize() {
        return connectionMinimumIdleSize;
    }

    public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) {
        RedissonConfig.connectionMinimumIdleSize = connectionMinimumIdleSize;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        RedissonConfig.connectionPoolSize = connectionPoolSize;
    }

    public int getSubscriptionsPerConnection() {
        return subscriptionsPerConnection;
    }

    public void setSubscriptionsPerConnection(int subscriptionsPerConnection) {
        RedissonConfig.subscriptionsPerConnection = subscriptionsPerConnection;
    }

    public int getSubscriptionConnectionMinimumIdleSize() {
        return subscriptionConnectionMinimumIdleSize;
    }

    public void setSubscriptionConnectionMinimumIdleSize(int subscriptionConnectionMinimumIdleSize) {
        RedissonConfig.subscriptionConnectionMinimumIdleSize = subscriptionConnectionMinimumIdleSize;
    }

    public int getSubscriptionConnectionPoolSize() {
        return subscriptionConnectionPoolSize;
    }

    public void setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
        RedissonConfig.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
    }

    // 其他配置
    public int getDnsMonitoringInterval() {
        return dnsMonitoringInterval;
    }

    public void setDnsMonitoringInterval(int dnsMonitoringInterval) {
        RedissonConfig.dnsMonitoringInterval = dnsMonitoringInterval;
    }

    public int getThread() {
        return thread;
    }

    public void setThread(int thread) {
        RedissonConfig.thread = thread;
    }

    public static String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        RedissonConfig.codec = codec;
    }
}