-- 移除 department_interview 主表上已废弃的 score/evaluation 字段。
-- 面试评价已由 department_interview_evaluation 表按 (interview_id, admin_cas_id)
-- 存多份独立评价，主表单条字段不再使用。
ALTER TABLE `department_interview`
  DROP CHECK `chk_interview_score`,
  DROP COLUMN `score`,
  DROP COLUMN `evaluation`;
