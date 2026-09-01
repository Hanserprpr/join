package cn.sduonline.join.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 学院与专业字典服务。
 * <p>
 * 启动时从类路径 {@code college-majors.json} 加载降级快照，
 * Nacos 发布新配置后原子替换当前快照，供列表查询与资料校验复用。
 */
@Slf4j
@Service
public class CollegeMajorService {

    private static final String RESOURCE = "college-majors.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 学院 -> 专业列表；每个快照不可变，替换过程对并发请求原子可见。 */
    private final AtomicReference<Map<String, List<String>>> collegeMajors;

    public CollegeMajorService() {
        Map<String, List<String>> fallback = loadResource();
        this.collegeMajors = new AtomicReference<>(fallback);
        log.info("Loaded {} colleges from fallback {}", fallback.size(), RESOURCE);
    }

    private static Map<String, List<String>> loadResource() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            Map<String, List<String>> parsed = OBJECT_MAPPER.readValue(
                    in, new TypeReference<LinkedHashMap<String, List<String>>>() {}
            );
            return validateAndFreeze(parsed);
        } catch (IOException ex) {
            throw new IllegalStateException("加载学院专业字典失败：" + RESOURCE, ex);
        }
    }

    /**
     * 校验 Nacos JSON 并原子切换快照。校验失败时抛出异常，旧快照保持不变。
     */
    void replaceFromJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Nacos 学院专业配置不能为空");
        }
        try {
            Map<String, List<String>> parsed = OBJECT_MAPPER.readValue(
                    content,
                    new TypeReference<LinkedHashMap<String, List<String>>>() {}
            );
            Map<String, List<String>> next = validateAndFreeze(parsed);
            collegeMajors.set(next);
            log.info("Applied Nacos college-major snapshot with {} colleges", next.size());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Nacos 学院专业配置不是有效 JSON", ex);
        }
    }

    private static Map<String, List<String>> validateAndFreeze(
            Map<String, List<String>> parsed
    ) {
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalArgumentException("学院专业字典不能为空");
        }
        LinkedHashMap<String, List<String>> immutable = new LinkedHashMap<>();
        parsed.forEach((rawCollege, rawMajors) -> {
            String college = requireText(rawCollege, "学院名称");
            if (rawMajors == null || rawMajors.isEmpty()) {
                throw new IllegalArgumentException("学院必须至少包含一个专业：" + college);
            }
            Set<String> seen = new HashSet<>();
            List<String> majors = rawMajors.stream()
                    .map(major -> requireText(major, "专业名称"))
                    .peek(major -> {
                        if (!seen.add(major)) {
                            throw new IllegalArgumentException(
                                    "学院中存在重复专业：" + college + "/" + major
                            );
                        }
                    })
                    .toList();
            if (immutable.putIfAbsent(college, List.copyOf(majors)) != null) {
                throw new IllegalArgumentException("存在重复学院：" + college);
            }
        });
        return Collections.unmodifiableMap(immutable);
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }

    /**
     * 获取全部学院及其专业列表。
     *
     * @return 学院到专业列表的不可变映射
     */
    public Map<String, List<String>> getCollegeMajors() {
        return collegeMajors.get();
    }

    /**
     * 判断学院是否存在于字典中。
     *
     * @param college 学院名称
     * @return 学院存在返回 true
     */
    public boolean isValidCollege(String college) {
        return StringUtils.hasText(college)
                && collegeMajors.get().containsKey(college.trim());
    }

    /**
     * 判断专业是否属于指定学院。
     *
     * @param college 学院名称
     * @param major 专业名称
     * @return 学院存在且专业属于该学院返回 true
     */
    public boolean isValidCollegeMajor(String college, String major) {
        if (!StringUtils.hasText(college) || !StringUtils.hasText(major)) {
            return false;
        }
        List<String> majors = collegeMajors.get().get(college.trim());
        return majors != null && majors.contains(major.trim());
    }
}
