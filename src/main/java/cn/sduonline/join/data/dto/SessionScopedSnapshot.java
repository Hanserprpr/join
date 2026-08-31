package cn.sduonline.join.data.dto;

/**
 * 能自报所属面试场次的 SSE 快照。
 * <p>
 * 候选人订阅在建立时不接收场次参数——学生随时可能取消签到再换场次签到，
 * 固定下来的场次会立刻过期。改由每次下发的快照回报当前场次，订阅据此
 * 自我校准：快照没有场次（例如已取消签到）时退回部门级，收到下一次任意
 * 场次的推送后重新收敛到新场次。
 */
public interface SessionScopedSnapshot {

    /** 快照所属场次；为 null 表示当前不属于任何场次。 */
    Long sessionId();
}
