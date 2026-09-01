package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DashboardOverviewVO;
import cn.sduonline.join.data.dto.ResultDashboardOverviewVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.service.DashboardStatisticsService;
import cn.sduonline.join.service.ServiceResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class DashboardStatisticsController {

    private final DashboardStatisticsService statisticsService;

    @GetMapping("/overview")
    @Operation(
            operationId = "getStatisticsOverview",
            description = "按服务端解析的板块、工作站或部门范围返回全量报名"
                    + "聚合数据。需要 statistics:read 且一次请求的整个组织范围必须"
                    + "属于调用者授权范围。成功业务码为 0；参数错误 40000，"
                    + "未登录 -4，登录失效 40101，越权 40103，组织不存在或已停用 140009。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功或已包装的业务错误",
                    content = @Content(schema = @Schema(
                            implementation = ResultDashboardOverviewVO.class
                    ))
            ),
            @ApiResponse(responseCode = "400", description = "请求参数无效"),
            @ApiResponse(responseCode = "401", description = "未登录或登录状态失效"),
            @ApiResponse(responseCode = "403", description = "没有统计权限或组织范围越权"),
            @ApiResponse(responseCode = "500", description = "服务内部异常")
    })
    public Result<DashboardOverviewVO> getOverview(
            @Parameter(description = "统计范围类型", required = true)
            @RequestParam OrgType scopeType,
            @Parameter(description = "板块、工作站或部门 ID", required = true)
            @RequestParam @Positive Long scopeId
    ) {
        ServiceResult<DashboardOverviewVO> result = statisticsService.getOverview(
                StpUtil.getLoginIdAsString(), scopeType, scopeId
        );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
