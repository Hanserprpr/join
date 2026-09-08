package cn.sduonline.join.data.dto;

import cn.sduonline.join.validation.HalfPointScore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 面试评分和评价请求
 *
 * @param score 面试评分，范围为 0.5 至 5，支持 0.5 分步进
 * @param evaluation 面试评价，最多 2000 个字符
 */
public record InterviewEvaluationRequest(
        @NotNull @HalfPointScore BigDecimal score,
        @Size(max = 2000) String evaluation
) {
}
