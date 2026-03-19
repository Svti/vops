-- vops 测试初始化数据（在 schema 建表之后执行）
-- 使用前请先执行 schema.sql 建库建表
--
-- 密码规则：与 ShiroConfig 一致，HashedCredentialsMatcher SHA-256、1 次迭代、Base64。
-- 生成方式（salt 与 password 拼接后做 SHA256 再 Base64）：
--   echo -n "<salt><password>" | openssl dgst -sha256 -binary | base64
-- 例如 admin：salt=vops, password=admin123 → echo -n "vopsadmin123" | openssl dgst -sha256 -binary | base64

USE vops;

-- 管理员用户（表单登录：用户名 admin，密码 admin123）
INSERT INTO sys_user (username, password, salt, real_name, status, create_time, update_time, deleted)
VALUES ('admin', 'PJfX0aFvbv9cSbT/CHDvhg7TyWWnwlm+NgaoMsqrPR0=', 'vops', '管理员', 1, NOW(), NOW(), 0);
-- 测试用户（用户名 test，密码 test123，salt 为 test）
INSERT INTO sys_user (username, password, salt, real_name, status, create_time, update_time, deleted)
VALUES ('test', 'YmqmAQDjiisKjJs1EhFr+z/521uYayGyfCmJ4BFilDU=', 'test', '测试用户', 1, NOW(), NOW(), 0);

-- 角色
INSERT INTO sys_role (role_code, role_name, description, create_time, update_time, deleted)
VALUES ('admin', '管理员', '系统管理员', NOW(), NOW(), 0),
       ('operator', '运维', '运维人员', NOW(), NOW(), 0);

-- 权限：host:all 表示所有主机，host:1 表示主机 ID=1
INSERT INTO sys_permission (perm_code, perm_name, resource_type, resource_id, create_time, update_time, deleted)
VALUES ('host:all', '全部主机', 'host', NULL, NOW(), NOW(), 0),
       ('host:read', '查看主机', 'host', NULL, NOW(), NOW(), 0);

-- 用户角色：admin 用户 -> 管理员角色，test 用户 -> 运维角色
INSERT INTO sys_user_role (user_id, role_id, create_time, deleted)
SELECT u.id, r.id, NOW(), 0 FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'admin';
INSERT INTO sys_user_role (user_id, role_id, create_time, deleted)
SELECT u.id, r.id, NOW(), 0 FROM sys_user u, sys_role r
WHERE u.username = 'test' AND r.role_code = 'operator';

-- 角色权限：管理员拥有 host:all，运维拥有 host:read
INSERT INTO sys_role_permission (role_id, permission_id, create_time, deleted)
SELECT r.id, p.id, NOW(), 0 FROM sys_role r, sys_permission p
WHERE r.role_code = 'admin' AND p.perm_code = 'host:all';
INSERT INTO sys_role_permission (role_id, permission_id, create_time, deleted)
SELECT r.id, p.id, NOW(), 0 FROM sys_role r, sys_permission p
WHERE r.role_code = 'operator' AND p.perm_code = 'host:read';

-- 主机分组
INSERT INTO host_group (name, description, parent_id, create_time, update_time, deleted)
VALUES ('默认分组', '默认', NULL, NOW(), NOW(), 0);

-- 可选：添加一台测试主机（密码为明文，保存时会被 AES 加密；仅测试用）
-- 实际密码请按应用配置的 vops.encrypt.key 加密后写入，或通过界面添加主机
-- INSERT INTO host (group_id, name, hostname, port, username, credential, auth_type, description, status, create_time, update_time, deleted)
-- VALUES (1, '本机', '127.0.0.1', 22, 'your_user', 'plain_password', 'password', '测试', 1, NOW(), NOW(), 0);
