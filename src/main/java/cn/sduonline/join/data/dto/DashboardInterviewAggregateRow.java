package cn.sduonline.join.data.dto;

/** 部门面试场次与等待人数的聚合查询结果。 */
public record DashboardInterviewAggregateRow(
        Integer publishedSessionCount,
        Integer operationalSessionCount,
        Long waitingCount
) {
}
