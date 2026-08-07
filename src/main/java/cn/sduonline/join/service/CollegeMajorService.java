package cn.sduonline.join.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 学院与专业字典服务。
 * <p>
 * 启动时从类路径 {@code college-majors.json} 加载学院到专业列表的映射，
 * 供列表查询接口与个人资料校验复用。
 */
@Slf4j
@Service
public class CollegeMajorService {

    private static final String RESOURCE = "college-majors.json";

    /** 学院 -> 专业列表，保持 JSON 中的原始顺序，且不可变。 */
    private final Map<String, List<String>> collegeMajors;

    public CollegeMajorService() {
        this.collegeMajors = load();
        log.info("Loaded {} colleges from {}", collegeMajors.size(), RESOURCE);
    }

    private static Map<String, List<String>> load() {
        ObjectMapper objectMapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            Map<String, List<String>> parsed = objectMapper.readValue(
                    in, new TypeReference<LinkedHashMap<String, List<String>>>() {}
            );
            LinkedHashMap<String, List<String>> immutable = new LinkedHashMap<>();
            parsed.forEach((college, majors) ->
                    immutable.put(college, List.copyOf(majors)));
            return Collections.unmodifiableMap(immutable);
        } catch (IOException ex) {
            throw new IllegalStateException("加载学院专业字典失败：" + RESOURCE, ex);
        }
    }

    /**
     * 获取全部学院及其专业列表。
     *
     * @return 学院到专业列表的不可变映射
     */
    public Map<String, List<String>> getCollegeMajors() {
        return collegeMajors;
    }

    /**
     * 判断学院是否存在于字典中。
     *
     * @param college 学院名称
     * @return 学院存在返回 true
     */
    public boolean isValidCollege(String college) {
        return StringUtils.hasText(college)
                && collegeMajors.containsKey(college.trim());
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
        List<String> majors = collegeMajors.get(college.trim());
        return majors != null && majors.contains(major.trim());
    }
}
