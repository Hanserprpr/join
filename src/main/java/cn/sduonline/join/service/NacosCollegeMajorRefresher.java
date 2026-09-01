package cn.sduonline.join.service;

import cn.sduonline.join.config.AppProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从 Nacos 读取学院专业快照并持续监听变更。
 * 连接、鉴权或配置校验失败时保留最后一个可用快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.college-majors.nacos",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NacosCollegeMajorRefresher {

    private final CollegeMajorService collegeMajorService;
    private final AppProperties appProperties;

    private volatile ConfigService configService;

    @Scheduled(
            initialDelayString = "0",
            fixedDelayString = "${app.college-majors.nacos.retry-delay-ms:30000}"
    )
    public synchronized void subscribeIfNecessary() {
        if (configService != null) {
            return;
        }

        AppProperties.Nacos settings = appProperties.getCollegeMajors().getNacos();
        ConfigService candidate = null;
        try {
            validateSettings(settings);
            candidate = NacosFactory.createConfigService(properties(settings));
            Listener listener = listener();
            ResolvedConfig resolved = resolveAndSubscribe(
                    candidate, settings, listener
            );
            collegeMajorService.replaceFromJson(resolved.content());
            configService = candidate;
            log.info(
                    "Subscribed to Nacos college-major config: namespace={}, group={}, dataId={}",
                    settings.getNamespace().trim(),
                    settings.getGroup().trim(),
                    resolved.dataId()
            );
        } catch (Exception ex) {
            shutdown(candidate);
            log.warn(
                    "Unable to subscribe to Nacos college-major config; "
                            + "keeping the current snapshot and retrying later: {}",
                    ex.getMessage()
            );
        }
    }

    private static ResolvedConfig resolveAndSubscribe(
            ConfigService service,
            AppProperties.Nacos settings,
            Listener listener
    ) throws NacosException {
        String configured = settings.getDataId();
        String normalized = configured.trim();
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(configured);
        candidates.add(normalized);
        // 兼容控制台中曾误建为带一个尾随空格的 Data ID。
        candidates.add(normalized + " ");

        String group = settings.getGroup().trim();
        for (String dataId : candidates) {
            String content = service.getConfigAndSignListener(
                    dataId, group, settings.getTimeoutMs(), listener
            );
            if (StringUtils.hasText(content)) {
                if (!dataId.equals(normalized)) {
                    log.warn(
                            "Using non-normalized Nacos dataId '{}'; rename it to '{}'",
                            dataId, normalized
                    );
                }
                return new ResolvedConfig(dataId, content);
            }
            service.removeListener(dataId, group, listener);
        }
        throw new IllegalStateException(
                "Nacos college-major config was not found or is empty"
        );
    }

    private Listener listener() {
        return new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                try {
                    collegeMajorService.replaceFromJson(configInfo);
                } catch (RuntimeException ex) {
                    log.error(
                            "Rejected invalid Nacos college-major update; "
                                    + "keeping the previous snapshot: {}",
                            ex.getMessage()
                    );
                }
            }
        };
    }

    private static Properties properties(AppProperties.Nacos settings) {
        Properties properties = new Properties();
        properties.setProperty(
                PropertyKeyConst.SERVER_ADDR,
                settings.getServerAddr().trim()
        );
        properties.setProperty(
                PropertyKeyConst.NAMESPACE,
                settings.getNamespace().trim()
        );
        if (StringUtils.hasText(settings.getUsername())) {
            properties.setProperty(
                    PropertyKeyConst.USERNAME,
                    settings.getUsername().trim()
            );
        }
        if (StringUtils.hasText(settings.getPassword())) {
            properties.setProperty(PropertyKeyConst.PASSWORD, settings.getPassword());
        }
        return properties;
    }

    private static void validateSettings(AppProperties.Nacos settings) {
        requireText(settings.getServerAddr(), "Nacos server address");
        requireText(settings.getNamespace(), "Nacos namespace");
        requireText(settings.getDataId(), "Nacos data ID");
        requireText(settings.getGroup(), "Nacos group");
        if (settings.getTimeoutMs() <= 0) {
            throw new IllegalArgumentException("Nacos timeout must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    @PreDestroy
    public synchronized void close() {
        ConfigService current = configService;
        configService = null;
        shutdown(current);
    }

    private static void shutdown(ConfigService service) {
        if (service == null) {
            return;
        }
        try {
            service.shutDown();
        } catch (NacosException ex) {
            log.debug("Failed to shut down Nacos config client", ex);
        }
    }

    private record ResolvedConfig(String dataId, String content) {
    }
}
