#!/usr/bin/env bash

set -Eeuo pipefail

readonly department_id=1

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "${script_dir}/.env" ]]; then
  env_file="${script_dir}/.env"
else
  env_file="$(cd -- "${script_dir}/.." && pwd)/.env"
fi
skip_confirmation=false

usage() {
  cat <<'EOF'
用法: scripts/cleanup-department-1-applications.sh [--env PATH] [--yes]

删除部门 1 的报名、录取邮件、签到、面试和评价数据。
保留用户、组织、部门、问卷、面试场次和管理员数据。

选项:
  --env PATH  从指定的 env 文件读取 DB_URL/DB_USERNAME/DB_PASSWORD
  --yes       跳过交互确认（仅建议用于受控环境）
  -h, --help  显示帮助
EOF
}

while (($# > 0)); do
  case "$1" in
    --env)
      if (($# < 2)); then
        echo "错误: --env 需要文件路径" >&2
        exit 2
      fi
      env_file="$2"
      shift 2
      ;;
    --yes)
      skip_confirmation=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "错误: 未知参数 $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "$env_file" ]]; then
  echo "错误: 找不到 env 文件: $env_file" >&2
  exit 1
fi

read_env_value() {
  local key="$1"
  local line value=""

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    if [[ "$line" == "${key}="* ]]; then
      value="${line#*=}"
    fi
  done < "$env_file"

  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi

  printf '%s' "$value"
}

db_url="$(read_env_value DB_URL)"
db_username="$(read_env_value DB_USERNAME)"
db_password="$(read_env_value DB_PASSWORD)"

if [[ -z "$db_url" ]]; then
  echo "错误: $env_file 中未配置 DB_URL" >&2
  exit 1
fi
if [[ -z "$db_username" ]]; then
  echo "错误: $env_file 中未配置 DB_USERNAME" >&2
  exit 1
fi
if command -v mysql >/dev/null 2>&1; then
  mysql_client="mysql"
elif command -v mariadb >/dev/null 2>&1; then
  mysql_client="mariadb"
else
  echo "错误: 未找到 mysql 或 mariadb 命令。" >&2
  exit 1
fi

if [[ "$db_url" =~ ^jdbc:mysql://([^/:?]+)(:([0-9]+))?/([^?]+)(\?.*)?$ ]]; then
  db_host="${BASH_REMATCH[1]}"
  db_port="${BASH_REMATCH[3]:-3306}"
  db_name="${BASH_REMATCH[4]}"
else
  echo "错误: 无法解析 DB_URL: $db_url" >&2
  echo "期望格式: jdbc:mysql://host[:port]/database?params" >&2
  exit 1
fi

mysql_defaults="$(mktemp "${TMPDIR:-/tmp}/join-department-cleanup.XXXXXX")"
cleanup_temp_file() {
  rm -f -- "$mysql_defaults"
}
trap cleanup_temp_file EXIT INT TERM
chmod 600 "$mysql_defaults"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\ndatabase=%s\ndefault-character-set=utf8mb4\n' \
  "$db_host" "$db_port" "$db_username" "$db_password" "$db_name" > "$mysql_defaults"

mysql_cmd=("$mysql_client" --defaults-extra-file="$mysql_defaults" --batch --raw --skip-column-names)

echo "目标 MySQL: ${db_username}@${db_host}:${db_port}/${db_name}"
echo "目标部门: ${department_id}"
echo
echo "待删除数量:"
"${mysql_cmd[@]}" <<SQL
SELECT CONCAT('  报名: ', COUNT(*))
FROM department_application
WHERE department_id = ${department_id};
SELECT CONCAT('  录取邮件: ', COUNT(*))
FROM admission_email_outbox o
JOIN department_application a ON a.id = o.application_id
WHERE a.department_id = ${department_id};
SELECT CONCAT('  签到: ', COUNT(*))
FROM department_check_in
WHERE department_id = ${department_id};
SELECT CONCAT('  面试: ', COUNT(*))
FROM department_interview
WHERE department_id = ${department_id};
SQL

if [[ "$skip_confirmation" != true ]]; then
  echo
  read -r -p "此操作会永久删除部门 ${department_id} 的上述数据。请输入 DELETE DEPARTMENT ${department_id} 继续: " confirmation
  if [[ "$confirmation" != "DELETE DEPARTMENT ${department_id}" ]]; then
    echo "已取消，数据库未修改。"
    exit 0
  fi
fi

"${mysql_cmd[@]}" <<SQL
START TRANSACTION;

CREATE TEMPORARY TABLE target_application_ids (
  id BIGINT NOT NULL PRIMARY KEY
);

INSERT INTO target_application_ids (id)
SELECT id
FROM department_application
WHERE department_id = ${department_id};

DELETE ia
FROM department_interview_active ia
JOIN department_interview i ON i.id = ia.interview_id
JOIN target_application_ids target ON target.id = i.application_id;

DELETE ie
FROM department_interview_evaluation ie
JOIN department_interview i ON i.id = ie.interview_id
JOIN target_application_ids target ON target.id = i.application_id;

DELETE i
FROM department_interview i
JOIN target_application_ids target ON target.id = i.application_id;

DELETE carryover
FROM department_interview_carryover carryover
JOIN target_application_ids target ON target.id = carryover.application_id;

DELETE check_in
FROM department_check_in check_in
JOIN target_application_ids target ON target.id = check_in.application_id;

DELETE outbox
FROM admission_email_outbox outbox
JOIN target_application_ids target ON target.id = outbox.application_id;

DELETE application
FROM department_application application
JOIN target_application_ids target ON target.id = application.id;

DROP TEMPORARY TABLE target_application_ids;

COMMIT;
SQL

echo
echo "清理完成。部门 ${department_id} 的报名、录取及关联流程数据已删除。"
