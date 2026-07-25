package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.WorkstationDetailVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.WorkstationService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作站接口
 */
@Validated
@RestController
@RequestMapping("/api/workstations")
@RequiredArgsConstructor
public class WorkstationController {

    private final WorkstationService workstationService;

    /**
     * 获取工作站及其部门列表
     *
     * @param workstationId 工作站 ID
     * @return 工作站详情
     */
    @GetMapping("/{workstationId}")
    public Result<WorkstationDetailVO> getWorkstation(
            @PathVariable @Positive Long workstationId
    ) {
        ServiceResult<WorkstationDetailVO> result =
                workstationService.findById(workstationId);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
