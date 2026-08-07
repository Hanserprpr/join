package cn.sduonline.join.controller;

import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.CollegeMajorService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学院与专业字典接口
 */
@RestController
@RequestMapping("/api/college-majors")
@RequiredArgsConstructor
public class CollegeMajorController {

    private final CollegeMajorService collegeMajorService;

    /**
     * 学院及其专业列表
     */
    @GetMapping
    public Result<Map<String, List<String>>> getCollegeMajors() {
        return Result.ok(collegeMajorService.getCollegeMajors());
    }
}
