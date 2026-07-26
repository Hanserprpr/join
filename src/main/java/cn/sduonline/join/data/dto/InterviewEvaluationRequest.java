package cn.sduonline.join.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 面试评分和评价请求
 *
 * @param score 面试评分，范围为 1 至 5
 * @param evaluation 面试评价，最多 2000 个字符
 */
public record InterviewEvaluationRequest(
        @Min(1) @Max(5) Integer score,
        @Size(max = 2000) String evaluation
) {
}
