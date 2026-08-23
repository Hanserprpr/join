package cn.sduonline.join.data.dto;

/**
 * 角色授权成员信息。
 * <p>
 * 每条记录对应一条原始角色授权，而不是把继承到目标节点的身份扁平为部门直授。
 * {@code scopeType} 还可能为 {@code ALL}，因此以字符串返回。
 *
 * @param id 角色授权记录 ID
 * @param casId 学号/统一认证账号
 * @param name 姓名
 * @param roleCode 身份编码
 * @param roleName 身份名称
 * @param scopeType 授权作用域类型（ALL/BOARD/WORKSTATION/DEPARTMENT）
 * @param scopeId 授权作用域 ID；ALL 时为 0
 * @param scopeName 授权作用域名称
 * @param boardId 该授权所在板块 ID；ALL 时为空
 * @param boardName 该授权所在板块名称；ALL 时为空
 * @param workstationId 该授权所在工作站 ID；板块/ALL 作用域时为空
 * @param workstationName 该授权所在工作站名称；板块/ALL 作用域时为空
 * @param departmentId 该授权所在部门 ID；非部门作用域时为空
 * @param departmentName 该授权所在部门名称；非部门作用域时为空
 */
public record RoleAssignmentMemberVO(
        Long id,
        String casId,
        String name,
        String roleCode,
        String roleName,
        String scopeType,
        Long scopeId,
        String scopeName,
        Long boardId,
        String boardName,
        Long workstationId,
        String workstationName,
        Long departmentId,
        String departmentName
) {
}
