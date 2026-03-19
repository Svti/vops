package com.vti.vops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostGroup;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 主机服务接口，继承 MyBatis-Plus IService，扩展权限与加解密能力。
 */
public interface IHostService extends IService<Host> {

    /** 按私钥 ID 分组返回关联主机（用于私钥管理页展示/防误删） */
    Map<Long, List<Host>> listHostsGroupedBySshKeyIds(Collection<Long> sshKeyIds);

    List<Host> listByUserIdWithPermission(Long userId);

    Host getByIdWithDecryptedCredential(Long id);

    List<Host> listForUser(Long userId);

    /** 按权限列出主机（含已禁用的），用于主机列表页与权限校验 */
    List<Host> listForUserIncludeDisabled(Long userId);

    List<HostGroup> listGroups();

    /** 统计属于该分组的主机数（用于分组管理页与删除前校验） */
    long countByGroupId(Long groupId);

    /** 根据分组 ID 列表返回这些分组下所有主机的 ID（去重，用于按分组执行） */
    List<Long> listHostIdsByGroupIds(List<Long> groupIds);

    /** 新增或更新分组 */
    void saveGroup(HostGroup group);

    /** 按名称查找分组，不存在则创建并返回 ID；name 为空返回 null */
    Long getOrCreateGroupByName(String name);

    /** 删除分组（若该分组下有关联主机则抛出 IllegalStateException） */
    void removeGroupById(Long id);

    /** 批量设置主机分组（groupId 为 null 表示移出分组） */
    int batchSetGroupIds(List<Long> hostIds, Long groupId);

    /** 批量设置认证方式（sshKeyId 为 null 表示密码认证或清除私钥；plainPassword 选填，为密码认证时填写则统一设置该密码并加密） */
    int batchSetAuth(List<Long> hostIds, String authType, Long sshKeyId, String plainPassword);

    List<Host> listAllEnabled();
}
