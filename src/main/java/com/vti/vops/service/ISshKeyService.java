package com.vti.vops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.SshKey;

import java.util.List;

/**
 * SSH 私钥库服务：统一管理私钥，主机可选择使用
 */
public interface ISshKeyService extends IService<SshKey> {

    /** 仅返回 id、name，供下拉选择（不包含 content） */
    List<SshKey> listNames();

    /** 根据 ID 获取解密后的私钥内容（仅内部连接时使用） */
    String getDecryptedContent(Long id);
}
