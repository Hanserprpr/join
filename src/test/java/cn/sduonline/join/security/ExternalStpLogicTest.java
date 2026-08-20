package cn.sduonline.join.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalStpLogicTest {

    private StpLogic originalLogic;

    @BeforeEach
    void saveOriginalLogic() {
        originalLogic = StpUtil.getStpLogic();
    }

    @AfterEach
    void restoreOriginalLogic() {
        StpUtil.setStpLogic(originalLogic);
    }

    @Test
    void registerAsDefaultReplacesStpUtilLogic() {
        ExternalStpLogic logic = new ExternalStpLogic(
                mock(ExternalTokenAuthenticationService.class)
        );

        logic.registerAsDefaultStpLogic();

        assertThat(StpUtil.getStpLogic()).isSameAs(logic);
    }
}
