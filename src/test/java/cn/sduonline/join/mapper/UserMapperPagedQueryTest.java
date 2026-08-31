package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * 用户分页查询的动态 SQL 契约：真实渲染 {@code <script>}，
 * 覆盖筛选条件的拼装、排序白名单和 LIKE 转义声明。
 */
class UserMapperPagedQueryTest {

    private static final Configuration CONFIGURATION = new Configuration();

    @Test
    void noFilterRendersWithoutWhereClause() {
        String sql = render("selectUsers", params());

        assertFalse(sql.contains("WHERE"));
        assertTrue(sql.contains("LIMIT ?"));
        assertTrue(sql.contains("OFFSET ?"));
    }

    @Test
    void eachFilterAddsItsOwnCondition() {
        Map<String, Object> params = params();
        params.put("keyword", "张");
        params.put("college", "软件学院");
        params.put("major", "软件工程");
        params.put("grade", 2024);
        params.put("profileCompleted", Boolean.TRUE);

        String sql = render("selectUsers", params);

        assertTrue(sql.contains("name LIKE"));
        assertTrue(sql.contains("cas_id LIKE"));
        assertTrue(sql.contains("phone LIKE"));
        assertTrue(sql.contains("email LIKE"));
        assertTrue(sql.contains("qq LIKE"));
        assertTrue(sql.contains("college = ?"));
        assertTrue(sql.contains("major = ?"));
        assertTrue(sql.contains("grade = ?"));
        assertTrue(sql.contains("profile_completed = ?"));
    }

    @Test
    void keywordSearchDeclaresLikeEscapeCharacter() {
        Map<String, Object> params = params();
        params.put("keyword", "100!%");

        assertEquals(5, countOccurrences(render("selectUsers", params),
                "ESCAPE '!'"));
        assertEquals(5, countOccurrences(render("countUsers", params),
                "ESCAPE '!'"));
    }

    @Test
    void wechatBoundFiltersOnNullabilityOfOpenid() {
        Map<String, Object> bound = params();
        bound.put("wechatBound", Boolean.TRUE);
        Map<String, Object> unbound = params();
        unbound.put("wechatBound", Boolean.FALSE);

        assertTrue(render("selectUsers", bound)
                .contains("wechat_openid IS NOT NULL"));
        assertTrue(render("selectUsers", unbound)
                .contains("wechat_openid IS NULL"));
        assertFalse(render("selectUsers", params())
                .contains("wechat_openid IS"));
    }

    @Test
    void sortIsWhitelistedAndAlwaysTieBrokenByCasId() {
        assertTrue(orderBy("createdAt", "desc")
                .startsWith("ORDER BY created_at DESC, cas_id ASC"));
        assertTrue(orderBy("createdAt", "asc")
                .startsWith("ORDER BY created_at ASC, cas_id ASC"));
        assertTrue(orderBy("casId", "asc").startsWith("ORDER BY cas_id ASC"));
        assertTrue(orderBy("grade", "desc")
                .startsWith("ORDER BY grade IS NULL ASC, grade DESC, cas_id ASC"));
    }

    @Test
    void unknownSortValuesFallBackInsteadOfReachingSql() {
        String sql = orderBy("cas_id; DROP TABLE `user`", "desc");

        assertTrue(sql.startsWith("ORDER BY created_at DESC, cas_id ASC"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    private String orderBy(String sortBy, String sortOrder) {
        Map<String, Object> params = params();
        params.put("sortBy", sortBy);
        params.put("sortOrder", sortOrder);
        String sql = render("selectUsers", params);
        return sql.substring(sql.indexOf("ORDER BY"));
    }

    /** 按参数渲染 Mapper 方法上的 {@code @Select} 动态 SQL。 */
    private static String render(String methodName, Map<String, Object> params) {
        Method method = List.of(UserMapper.class.getMethods()).stream()
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        String script = String.join("\n",
                method.getAnnotation(Select.class).value());
        SqlSource sqlSource = new XMLLanguageDriver()
                .createSqlSource(CONFIGURATION, script, Map.class);
        BoundSql boundSql = sqlSource.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object> params() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", null);
        params.put("college", null);
        params.put("major", null);
        params.put("grade", null);
        params.put("profileCompleted", null);
        params.put("wechatBound", null);
        params.put("sortBy", "createdAt");
        params.put("sortOrder", "desc");
        params.put("offset", 0);
        params.put("limit", 20);
        return params;
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }
}
