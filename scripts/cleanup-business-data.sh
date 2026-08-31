#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "${script_dir}/.env" ]]; then
  # 脚本被放在项目根目录时使用同级 .env。
  env_file="${script_dir}/.env"
else
  # 仓库默认放在 scripts/ 目录，.env 位于上一级。
  env_file="$(cd -- "${script_dir}/.." && pwd)/.env"
fi
skip_confirmation=false

usage() {
  cat <<'EOF'
用法: scripts/cleanup-business-data.sh [--env PATH] [--yes]

清空报名、签到、面试等纳新业务数据，并删除没有管理员身份的普通用户。
保留组织与部门资料、管理员账号与授权、角色权限以及 Flyway 记录。

选项:
  --env PATH  从指定的 env 文件读取 DB_URL/DB_USERNAME/DB_PASSWORD
  --yes       跳过交互确认（仅建议用于受控的自动化环境）
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
  echo "Ubuntu/Debian 可执行: apt-get update && apt-get install -y default-mysql-client" >&2
  exit 1
fi

# 支持项目使用的标准 JDBC URL：jdbc:mysql://host[:port]/database?params
if [[ "$db_url" =~ ^jdbc:mysql://([^/:?]+)(:([0-9]+))?/([^?]+)(\?.*)?$ ]]; then
  db_host="${BASH_REMATCH[1]}"
  db_port="${BASH_REMATCH[3]:-3306}"
  db_name="${BASH_REMATCH[4]}"
else
  echo "错误: 无法解析 DB_URL: $db_url" >&2
  echo "期望格式: jdbc:mysql://host[:port]/database?params" >&2
  exit 1
fi

mysql_defaults="$(mktemp "${TMPDIR:-/tmp}/join-cleanup.XXXXXX")"
cleanup_temp_file() {
  rm -f -- "$mysql_defaults"
}
trap cleanup_temp_file EXIT INT TERM
chmod 600 "$mysql_defaults"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\ndatabase=%s\ndefault-character-set=utf8mb4\n' \
  "$db_host" "$db_port" "$db_username" "$db_password" "$db_name" > "$mysql_defaults"

mysql_cmd=("$mysql_client" --defaults-extra-file="$mysql_defaults" --batch --raw --skip-column-names)

echo "目标 MySQL: ${db_username}@${db_host}:${db_port}/${db_name}"
echo
echo "待清理数量:"
"${mysql_cmd[@]}" <<'SQL'
SELECT CONCAT('  报名: ', COUNT(*)) FROM department_application;
SELECT CONCAT('  签到: ', COUNT(*)) FROM department_check_in;
SELECT CONCAT('  面试: ', COUNT(*)) FROM department_interview;
SELECT CONCAT('  面试场次: ', COUNT(*)) FROM department_interview_session;
SELECT CONCAT('  将删除的普通用户: ', COUNT(*))
FROM `user` u
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_scope urs WHERE urs.cas_id = u.cas_id
);
SELECT CONCAT('  将保留的管理员: ', COUNT(DISTINCT cas_id)) FROM user_role_scope;
SQL

if [[ "$skip_confirmation" != true ]]; then
  echo
  read -r -p "此操作会永久删除上述业务数据。请输入 CLEAN ${db_name} 继续: " confirmation
  if [[ "$confirmation" != "CLEAN ${db_name}" ]]; then
    echo "已取消，数据库未修改。"
    exit 0
  fi
fi

"${mysql_cmd[@]}" <<'SQL'
START TRANSACTION;

DELETE FROM department_interview_active;
DELETE FROM department_interview_evaluation;
DELETE FROM department_interview;
DELETE FROM department_interview_room_member;
DELETE FROM department_interview_carryover;
DELETE FROM department_check_in;
DELETE FROM department_check_in_sequence;
DELETE FROM department_interview_room;
DELETE FROM admission_email_outbox;
DELETE FROM department_application;
DELETE FROM department_interview_session;

DELETE u
FROM `user` u
LEFT JOIN user_role_scope urs ON urs.cas_id = u.cas_id
WHERE urs.cas_id IS NULL;

COMMIT;
SQL

echo
echo "清理完成。组织、部门资料、管理员身份、权限配置和 Flyway 记录均已保留。"
echo "建议重启应用以清除进程内的业务缓存。"
