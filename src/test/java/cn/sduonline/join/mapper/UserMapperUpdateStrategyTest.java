package cn.sduonline.join.mapper;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.sduonline.join.data.po.User;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 邮箱与 QQ 号清空必须真正落库。
 * <p>
 * MyBatis-Plus 的 {@code updateById} 默认按 {@code FieldStrategy.NOT_NULL}
 * 生成 SQL，会把值为 null 的列整个从 UPDATE 语句里剔除。实体上不显式声明
 * 更新策略的话，把字段置 null 只改内存、不改数据库——接口返回“已清空”，
 * 下次读库却仍是旧值。
 * <p>
 * 这里直接跑 H2，覆盖 mock 掉 mapper 的单元测试照不到的这一层。
 */
class UserMapperUpdateStrategyTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void bootstrap() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:user_update_strategy;MODE=MySQL"
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                // user 是 H2 的保留字；MyBatis-Plus 按 @TableName("user")
                // 生成的 SQL 不加引号，生产的 MySQL 能接受。
                + ";NON_KEYWORDS=USER");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection();
                Reader script = new InputStreamReader(
                        UserMapperUpdateStrategyTest.class
                                .getResourceAsStream("/schema.sql"),
                        StandardCharsets.UTF_8)) {
            RunScript.execute(connection, script);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        // 与 application.yaml 的 mybatis-plus 配置保持一致；生产同样没有覆盖
        // update-strategy，因此这里必须使用框架默认值，测试才有意义。
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.getDbConfig().setTableUnderline(true);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(UserMapper.class);

        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder()
                .build(configuration);
    }

    @BeforeEach
    void resetUser() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            if (mapper.selectById("20240001") != null) {
                mapper.deleteById("20240001");
            }
            User user = new User();
            user.setCasId("20240001");
            user.setName("张三");
            user.setEmail("student@sdu.edu.cn");
            user.setPhone("13900000000");
            user.setQq("123456");
            user.setCollege("软件学院");
            user.setMajor("软件工程");
            user.setGrade(2024);
            user.setProfileCompleted(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            mapper.insert(user);
        }
    }

    @Test
    void updateByIdPersistsClearedEmailAndQq() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            User user = mapper.selectById("20240001");
            user.setEmail(null);
            user.setQq(null);
            user.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(user);

            session.clearCache();
            User reloaded = mapper.selectById("20240001");
            assertNull(reloaded.getEmail(), "邮箱清空后必须落库为 NULL");
            assertNull(reloaded.getQq(), "QQ 号清空后必须落库为 NULL");
        }
    }

    @Test
    void updateByIdKeepsOtherFieldsWhenClearing() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            User user = mapper.selectById("20240001");
            user.setEmail(null);
            user.setPhone("13800000000");
            user.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(user);

            session.clearCache();
            User reloaded = mapper.selectById("20240001");
            assertNull(reloaded.getEmail());
            assertEquals("13800000000", reloaded.getPhone());
            assertEquals("123456", reloaded.getQq());
            assertEquals("软件学院", reloaded.getCollege());
            assertEquals(2024, reloaded.getGrade());
        }
    }

    /**
     * 必填字段仍走默认的 NOT_NULL 策略：置 null 不会把列清掉。
     * 这条约束住改动范围，避免顺手把整个实体都改成 IGNORED。
     */
    @Test
    void updateByIdIgnoresNullRequiredFields() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            User user = mapper.selectById("20240001");
            user.setPhone(null);
            user.setCollege(null);
            mapper.updateById(user);

            session.clearCache();
            User reloaded = mapper.selectById("20240001");
            assertEquals("13900000000", reloaded.getPhone());
            assertEquals("软件学院", reloaded.getCollege());
        }
    }
}
