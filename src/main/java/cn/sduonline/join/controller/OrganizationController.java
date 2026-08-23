package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.OrganizationTreeVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.BoardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开组织查询接口。
 */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final BoardService boardService;

    /**
     * 获取全部启用的板块、工作站、部门三级组织树。
     */
    @GetMapping
    public Result<List<OrganizationTreeVO>> getOrganizations() {
        return Result.ok(boardService.findEnabledOrganizationTree());
    }
}
