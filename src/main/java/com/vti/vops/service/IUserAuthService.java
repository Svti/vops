package com.vti.vops.service;

import java.util.List;

/**
 * 用户认证授权查询服务接口
 */
public interface IUserAuthService {

    List<String> getRoleCodesByUserId(Long userId);

    List<String> getPermCodesByUserId(Long userId);
}
