package cn.sduonline.join.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 登录态必须落在 Redis，否则重启会掉线、多实例之间也不共享。
 * Lettuce 连接工厂是懒连接，这里不需要真的连上 Redis。
 */
class SaTokenRedisDaoTest {

    @Test
    void saTokenStoresLoginStateInRedisInsteadOfMemory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataRedisAutoConfiguration.class,
                        SaTokenDaoForRedisTemplate.class
                ))
                .run(context -> assertThat(context)
                        .getBean(SaTokenDao.class)
                        .isInstanceOf(SaTokenDaoForRedisTemplate.class));
    }
}
