ALTER TABLE `department_interview_evaluation`
  DROP CHECK `chk_interview_evaluation_score`,
  MODIFY COLUMN `score` DECIMAL(2,1) NOT NULL;

ALTER TABLE `department_interview_evaluation`
  ADD CONSTRAINT `chk_interview_evaluation_score`
    CHECK (`score` BETWEEN 1.0 AND 5.0 AND MOD(`score` * 2, 1) = 0);
