package cn.sduonline.join.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * 部门海报写入参数
 * 保留已有海报时传 {@code id}，新增海报时传上传接口返回的 {@code url}，两者恰好其一
 *
 * @param id 已有海报 ID，表示保留该海报
 * @param url 新增海报地址
 * @param sortOrder 排序值，不传时按请求数组下标排序
 */
public record DepartmentPosterRequest(
        @Positive
        Long id,

        @Size(max = 2048)
        String url,

        @Min(0)
        Integer sortOrder
) {
    /** 校验 id 与 url 恰好提供其中之一。 */
    @JsonIgnore
    @AssertTrue(message = "海报必须且只能指定 id 或 url 之一")
    public boolean isExactlyOneReferencePresent() {
        return (id != null) ^ StringUtils.hasText(url);
    }
}
