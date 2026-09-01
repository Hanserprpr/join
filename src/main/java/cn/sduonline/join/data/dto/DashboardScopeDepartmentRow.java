package cn.sduonline.join.data.dto;

/** 组织范围解析查询的扁平行。 */
public record DashboardScopeDepartmentRow(
        Long scopeId,
        String scopeName,
        Long departmentId,
        String departmentName,
        Long workstationId,
        String workstationName
) {
}
