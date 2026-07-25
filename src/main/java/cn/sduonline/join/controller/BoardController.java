package cn.sduonline.join.controller;

import cn.sduonline.join.data.dto.BoardWithWorkstationsVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.BoardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 板块接口
 */
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 板块列表
     */
    @GetMapping
    public Result<List<BoardWithWorkstationsVO>> getBoards() {
        return Result.ok(boardService.findEnabledBoards());
    }
}
