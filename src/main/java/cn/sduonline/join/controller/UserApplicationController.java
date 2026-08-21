package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.MyApplicationVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.DepartmentApplicationService;
import cn.sduonline.join.service.ServiceResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequestMapping("/api/user/applications")
@RequiredArgsConstructor
public class UserApplicationController {

    private final DepartmentApplicationService applicationService;

    /**
     * 获取当前登录用户在所有部门的报名记录
     *
     * @return 报名记录列表，按报名时间倒序，未报名时为空列表
     */
    @GetMapping
    public Result<List<MyApplicationVO>> findMyApplications() {
        ServiceResult<List<MyApplicationVO>> result =
                applicationService.findMyApplications(StpUtil.getLoginIdAsString());
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
