package com.vti.vops.service;

import com.vti.vops.dto.HostListDataVo;
import com.vti.vops.dto.HostListMetaVo;

/**
 * 主机列表缓存服务：按用户缓存列表数据与全局元数据，定时刷新，打开列表时直接读缓存。
 */
public interface IHostListCacheService {

    HostListDataVo getHostListData(Long userId);

    HostListMetaVo getHostListMeta();

    void refreshAll();

    void refreshUser(Long userId);
}
