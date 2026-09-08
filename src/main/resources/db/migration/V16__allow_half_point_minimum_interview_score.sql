ALTER TABLE `department_interview_evaluation`
  DROP CHECK `chk_interview_evaluation_score`,
  ADD CONSTRAINT `chk_interview_evaluation_score`
    CHECK (`score` BETWEEN 0.5 AND 5.0 AND MOD(`score` * 2, 1) = 0);
