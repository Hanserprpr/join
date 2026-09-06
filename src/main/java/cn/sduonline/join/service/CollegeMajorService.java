package cn.sduonline.join.service;

import cn.sduonline.join.data.enums.Campus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
 * Nacos 发布新配置后原子替换当前快照，供列表查询、资料校验与校区推导复用。
 * <p>
 * 配置支持两种写法，加载时都会展平成同一份快照，
 * {@code /api/college-majors} 的返回结构因此与配置组织方式无关：
 * <ul>
 *   <li>校区键：{@code {"中心校区": {"文学院": ["汉语言文学"]}}}，
 *       校区结构性必填，是推荐写法；济南以外的校区（青岛、威海）同样按校区分组，
 *       只是没有枚举取值、推导不出校区；键写错即拒绝整份配置；</li>
 *   <li>学院键：{@code {"文学院": ["汉语言文学"]}}，
 *       旧格式，学院没有校区归属，{@link #resolveCampus} 返回 null。</li>
 * </ul>
 * 两种格式长期共存，配置可以随时回滚而不必发版。
 */
@Slf4j
@Service
public class CollegeMajorService {

    private static final String RESOURCE = "college-majors.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 济南以外的校区：配置里可以照常分组，但 {@link Campus} 没有对应取值，
     * 其下的学院不归属任何校区，{@link #resolveCampus} 返回 null。
     * <p>
     * 用一个显式白名单而不是“识别不了的键一律当没有校区”，
     * 是为了让写错的校区名照旧被拒绝，不会静默降级成无校区。
     */
    private static final Set<String> UNMAPPED_CAMPUSES = Set.of("青岛校区", "威海校区");

    /** 当前字典快照；每个快照不可变，替换过程对并发请求原子可见。 */
    private final AtomicReference<Snapshot> snapshot;

    public CollegeMajorService() {
        Snapshot fallback = loadResource();
        this.snapshot = new AtomicReference<>(fallback);
        log.info(
                "Loaded {} colleges ({} with a campus) from fallback {}",
                fallback.collegeMajors().size(),
                fallback.collegeCampuses().size(),
                RESOURCE
        );
    }

    private static Snapshot loadResource() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return parse(OBJECT_MAPPER.readTree(in));
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
        Snapshot next;
        try {
            next = parse(OBJECT_MAPPER.readTree(content));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Nacos 学院专业配置不是有效 JSON", ex);
        }
        snapshot.set(next);
        log.info(
                "Applied Nacos college-major snapshot with {} colleges ({} with a campus)",
                next.collegeMajors().size(),
                next.collegeCampuses().size()
        );
    }

    /**
     * 按顶层第一个值的类型判定配置格式：对象是校区键，数组是学院键。
     */
    private static Snapshot parse(JsonNode root) {
        if (root == null || !root.isObject() || root.isEmpty()) {
            throw new IllegalArgumentException("学院专业字典不能为空");
        }
        return root.elements().next().isObject()
                ? parseCampusKeyed(root)
                : parseCollegeKeyed(root);
    }

    private static Snapshot parseCampusKeyed(JsonNode root) {
        SnapshotBuilder builder = new SnapshotBuilder();
        root.properties().forEach(campusEntry -> {
            String key = requireText(campusEntry.getKey(), "校区名称");
            Campus campus = UNMAPPED_CAMPUSES.contains(key) ? null : Campus.fromValue(key);
            JsonNode colleges = campusEntry.getValue();
            if (!colleges.isObject() || colleges.isEmpty()) {
                throw new IllegalArgumentException("校区必须至少包含一个学院：" + key);
            }
            colleges.properties().forEach(collegeEntry -> builder.add(
                    collegeEntry.getKey(), collegeEntry.getValue(), campus
            ));
        });
        return builder.build();
    }

    private static Snapshot parseCollegeKeyed(JsonNode root) {
        SnapshotBuilder builder = new SnapshotBuilder();
        root.properties().forEach(entry ->
                builder.add(entry.getKey(), entry.getValue(), null));
        return builder.build();
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
        return snapshot.get().collegeMajors();
    }

    /**
     * 判断学院是否存在于字典中。
     *
     * @param college 学院名称
     * @return 学院存在返回 true
     */
    public boolean isValidCollege(String college) {
        return StringUtils.hasText(college)
                && snapshot.get().collegeMajors().containsKey(college.trim());
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
        List<String> majors = snapshot.get().collegeMajors().get(college.trim());
        return majors != null && majors.contains(major.trim());
    }

    /**
     * 按学院推导校区。
     *
     * @param college 学院名称
     * @return 对应校区；学院不存在或当前配置未给出校区时返回 null
     */
    public Campus resolveCampus(String college) {
        if (!StringUtils.hasText(college)) {
            return null;
        }
        return snapshot.get().collegeCampuses().get(college.trim());
    }

    /**
     * 展平后的字典快照。
     *
     * @param collegeMajors 学院到专业列表，接口按此返回
     * @param collegeCampuses 学院到校区，仅服务端使用；旧格式配置下为空
     */
    private record Snapshot(
            Map<String, List<String>> collegeMajors,
            Map<String, Campus> collegeCampuses
    ) {
    }

    /**
     * 逐个学院累积快照，顺带完成跨校区重名与重复专业的校验。
     */
    private static final class SnapshotBuilder {

        private final LinkedHashMap<String, List<String>> collegeMajors =
                new LinkedHashMap<>();

        private final LinkedHashMap<String, Campus> collegeCampuses =
                new LinkedHashMap<>();

        void add(String rawCollege, JsonNode majorsNode, Campus campus) {
            String college = requireText(rawCollege, "学院名称");
            if (collegeMajors.containsKey(college)) {
                throw duplicateCollege(college, campus);
            }
            collegeMajors.put(college, readMajors(majorsNode, college));
            if (campus != null) {
                collegeCampuses.put(college, campus);
            }
        }

        Snapshot build() {
            if (collegeMajors.isEmpty()) {
                throw new IllegalArgumentException("学院专业字典不能为空");
            }
            return new Snapshot(
                    Collections.unmodifiableMap(collegeMajors),
                    Collections.unmodifiableMap(collegeCampuses)
            );
        }

        private IllegalArgumentException duplicateCollege(
                String college, Campus campus
        ) {
            Campus previous = collegeCampuses.get(college);
            if (previous == null || campus == null) {
                return new IllegalArgumentException("存在重复学院：" + college);
            }
            // 同名学院分属两个校区时无法确定学生归属，整份配置拒绝
            return new IllegalArgumentException(
                    "学院同时出现在多个校区：" + college
                            + "（" + previous.getDisplayName()
                            + "、" + campus.getDisplayName() + "）"
            );
        }

        private static List<String> readMajors(JsonNode majorsNode, String college) {
            if (majorsNode == null || !majorsNode.isArray() || majorsNode.isEmpty()) {
                throw new IllegalArgumentException(
                        "学院必须至少包含一个专业：" + college
                );
            }
            Set<String> seen = new HashSet<>();
            List<String> majors = new ArrayList<>(majorsNode.size());
            for (JsonNode item : majorsNode) {
                String major = requireText(
                        item.isTextual() ? item.asText() : null, "专业名称"
                );
                if (!seen.add(major)) {
                    throw new IllegalArgumentException(
                            "学院中存在重复专业：" + college + "/" + major
                    );
                }
                majors.add(major);
            }
            return List.copyOf(majors);
        }
    }
}
