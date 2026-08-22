package cn.sduonline.join.data.enums;

/** 面试候选人过号后的处理方式。 */
public enum InterviewPassMode {
    /** 向后顺延指定人数。 */
    DELAY,
    /** 移出等待队列，候选人重新签到后排到队尾。 */
    RECHECK_IN
}
