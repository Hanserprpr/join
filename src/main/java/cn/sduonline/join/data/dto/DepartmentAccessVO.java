package cn.sduonline.join.data.dto;

import java.util.List;

/**
 * 当前用户在某个部门的访问信息
 *
 * @param departmentId 部门 ID
 * @param departmentName 部门名称
 * @param identities 该用户在部门中拥有的身份列表
 */
public record DepartmentAccessVO(
        Long departmentId,
        String departmentName,
        List<DepartmentIdentityVO> identities
) {
}
