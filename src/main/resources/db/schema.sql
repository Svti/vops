-- vops 初使库表（MySQL）
CREATE DATABASE IF NOT EXISTS vops DEFAULT CHARACTER SET utf8mb4;
USE vops;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  username VARCHAR(64) NOT NULL COMMENT '登录名',
  password VARCHAR(255) COMMENT '密码（SHA256+salt 加密）',
  salt VARCHAR(64) COMMENT '盐值',
  real_name VARCHAR(64) COMMENT '真实姓名',
  email VARCHAR(128) COMMENT '邮箱',
  oidc_sub VARCHAR(255) COMMENT 'OIDC 主体标识',
  status INT DEFAULT 1 COMMENT '状态：0 禁用 1 正常',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
  UNIQUE KEY uk_username (username),
  KEY idx_sys_user_oidc_sub (oidc_sub)
) COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(64) COMMENT '角色名称',
  description VARCHAR(255) COMMENT '描述',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  perm_code VARCHAR(64) NOT NULL COMMENT '权限编码',
  perm_name VARCHAR(64) COMMENT '权限名称',
  resource_type VARCHAR(32) COMMENT '资源类型，如 host',
  resource_id BIGINT COMMENT '资源 ID，主机级权限时为主机 ID',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='权限表';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户 ID',
  role_id BIGINT NOT NULL COMMENT '角色 ID',
  create_time DATETIME COMMENT '创建时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  role_id BIGINT NOT NULL COMMENT '角色 ID',
  permission_id BIGINT NOT NULL COMMENT '权限 ID',
  create_time DATETIME COMMENT '创建时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS host_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(64) COMMENT '分组名称',
  description VARCHAR(255) COMMENT '描述',
  parent_id BIGINT COMMENT '父分组 ID',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='主机分组表';

CREATE TABLE IF NOT EXISTS ssh_key (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(64) NOT NULL COMMENT '密钥名称',
  content TEXT COMMENT '私钥内容（AES 加密存储）',
  description VARCHAR(255) COMMENT '备注',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='SSH 私钥库（统一管理，添加主机时可选择）';

CREATE TABLE IF NOT EXISTS host (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  group_id BIGINT COMMENT '所属分组 ID',
  name VARCHAR(64) COMMENT '显示名称',
  hostname VARCHAR(255) COMMENT '主机名或 IP',
  port INT DEFAULT 22 COMMENT 'SSH 端口',
  username VARCHAR(64) COMMENT 'SSH 登录用户',
  credential TEXT COMMENT 'SSH 密码或私钥（AES 加密存储），使用私钥库时为空',
  ssh_key_id BIGINT COMMENT '关联私钥库 ID，非空时以私钥库为准',
  auth_type VARCHAR(32) COMMENT '认证方式：password / privateKey',
  description VARCHAR(255) COMMENT '备注',
  status INT DEFAULT 1 COMMENT '状态：0 禁用 1 启用',
  last_metric_time DATETIME COMMENT '最近一次采集时间，用于判断在线/离线',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='主机表';

CREATE TABLE IF NOT EXISTS host_metric (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  host_id BIGINT COMMENT '主机 ID',
  collect_time DATETIME COMMENT '采集时间',
  cpu_usage DOUBLE COMMENT 'CPU 使用率（%）',
  cpu_cores INT COMMENT 'CPU 核数',
  load_avg VARCHAR(64) COMMENT '系统平均负载 1/5/15 分钟',
  uptime_seconds BIGINT COMMENT '系统运行时间（秒）',
  mem_total BIGINT COMMENT '内存总量（字节）',
  mem_used BIGINT COMMENT '内存已用（字节）',
  mem_free BIGINT COMMENT '内存空闲（字节）',
  mem_usage_percent DOUBLE COMMENT '内存使用率（%）',
  disk_usage_percent INT COMMENT '根分区磁盘使用率（%）',
  disk_total BIGINT COMMENT '根分区磁盘总量（字节）',
  disk_used BIGINT COMMENT '根分区磁盘已用（字节）',
  disk_json TEXT COMMENT '磁盘使用 JSON',
  network_rx_rate_bps BIGINT COMMENT '网络入站速率（字节/秒）',
  network_tx_rate_bps BIGINT COMMENT '网络出站速率（字节/秒）',
  icmp_rtt_ms BIGINT COMMENT 'ICMP 往返时延（毫秒），9999 表示不可达',
  process_summary VARCHAR(512) COMMENT '进程摘要',
  process_top_cpu TEXT COMMENT 'CPU 前 5 进程 JSON',
  KEY idx_host_metric_host_time (host_id, collect_time),
  KEY idx_host_metric_collect_time (collect_time)
) COMMENT='主机指标历史表';

CREATE TABLE IF NOT EXISTS alert_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(64) COMMENT '规则名称',
  metric_key VARCHAR(32) COMMENT '指标键：cpu_usage / mem_usage 等',
  operator VARCHAR(16) COMMENT '比较符：gt / gte / lt / lte / eq',
  threshold DOUBLE COMMENT '阈值',
  duration_seconds INT COMMENT '持续秒数',
  severity INT COMMENT '严重程度',
  enabled INT DEFAULT 1 COMMENT '是否启用：0 否 1 是',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='告警规则表';

CREATE TABLE IF NOT EXISTS host_alert_rule (
  host_id BIGINT NOT NULL COMMENT '主机 ID',
  rule_id BIGINT NOT NULL COMMENT '规则 ID',
  PRIMARY KEY (host_id, rule_id),
  KEY idx_rule_id (rule_id)
) COMMENT='主机与告警规则关联表：在主机管理中配置该主机适用的规则';

CREATE TABLE IF NOT EXISTS alert_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  rule_id BIGINT COMMENT '规则 ID',
  host_id BIGINT COMMENT '主机 ID',
  metric_key VARCHAR(32) COMMENT '指标键',
  current_value DOUBLE COMMENT '当前值',
  threshold DOUBLE COMMENT '阈值',
  message VARCHAR(512) COMMENT '告警内容',
  severity INT COMMENT '严重程度',
  status INT COMMENT '状态：1 未处理等',
  create_time DATETIME COMMENT '创建时间',
  resolve_time DATETIME COMMENT '处理时间'
) COMMENT='告警事件表';

CREATE TABLE IF NOT EXISTS alert_notifier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(64) COMMENT '渠道名称',
  channel VARCHAR(32) COMMENT '渠道类型：dingtalk / feishu / wecom / email',
  config_json TEXT COMMENT '配置 JSON，如 webhook / to',
  enabled INT DEFAULT 1 COMMENT '是否启用：0 否 1 是',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='告警通知渠道表';

CREATE TABLE IF NOT EXISTS alert_rule_notifier (
  rule_id BIGINT NOT NULL COMMENT '告警规则 ID',
  notifier_id BIGINT NOT NULL COMMENT '通知渠道 ID',
  PRIMARY KEY (rule_id, notifier_id),
  KEY idx_rule_id (rule_id),
  KEY idx_notifier_id (notifier_id)
) COMMENT='告警规则与通知渠道关联表：在告警规则中配置该规则触发时使用的渠道';

CREATE TABLE IF NOT EXISTS batch_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  schedule_id BIGINT COMMENT '来源定时任务 ID，空表示手动执行',
  name VARCHAR(64) COMMENT '任务名称',
  command TEXT COMMENT '执行命令',
  host_ids VARCHAR(512) COMMENT '主机 ID 列表，逗号分隔',
  operator_id BIGINT COMMENT '操作人用户 ID',
  status INT COMMENT '状态：0 执行中 1 已完成',
  total_count INT COMMENT '总主机数',
  success_count INT COMMENT '成功数',
  fail_count INT COMMENT '失败数',
  create_time DATETIME COMMENT '创建时间',
  finish_time DATETIME COMMENT '完成时间'
) COMMENT='批量执行任务表（每次执行一条记录）';

CREATE TABLE IF NOT EXISTS batch_task_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  task_id BIGINT COMMENT '任务 ID',
  host_id BIGINT COMMENT '主机 ID',
  exit_code INT COMMENT '退出码',
  output LONGTEXT COMMENT '标准输出',
  error VARCHAR(1024) COMMENT '错误信息',
  create_time DATETIME COMMENT '创建时间'
) COMMENT='批量执行日志表';

CREATE TABLE IF NOT EXISTS batch_schedule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(64) COMMENT '任务名称',
  command TEXT COMMENT '执行命令',
  host_ids VARCHAR(512) COMMENT '主机 ID 列表，逗号分隔',
  group_ids VARCHAR(256) COMMENT '按分组时保存的分组 ID 列表，逗号分隔，用于回显',
  cron_expression VARCHAR(128) COMMENT 'Cron 表达式，如 0 0 2 * * ? 表示每天 2 点',
  enabled INT DEFAULT 1 COMMENT '是否启用：0 否 1 是',
  operator_id BIGINT COMMENT '创建人用户 ID',
  create_time DATETIME COMMENT '创建时间',
  update_time DATETIME COMMENT '更新时间',
  deleted INT DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除'
) COMMENT='批量执行定时任务表';

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT COMMENT '用户 ID',
  username VARCHAR(64) COMMENT '用户名',
  action VARCHAR(64) COMMENT '操作类型',
  resource_type VARCHAR(32) COMMENT '资源类型',
  resource_id VARCHAR(64) COMMENT '资源 ID',
  detail VARCHAR(512) COMMENT '详情',
  ip VARCHAR(64) COMMENT '客户端 IP',
  user_agent VARCHAR(512) COMMENT 'User-Agent',
  create_time DATETIME COMMENT '创建时间'
) COMMENT='操作审计日志表';

-- 默认管理员（密码 admin123，需 SHA256+salt 后写入）
-- INSERT INTO sys_user (username, password, salt, status, create_time, update_time, deleted) VALUES ('admin', '<hex>', '<salt>', 1, NOW(), NOW(), 0);