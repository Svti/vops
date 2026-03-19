package com.vti.vops.service;

import com.vti.vops.dto.IndexSummaryVo;

/**
 * 首页运维总览数据服务（带缓存）
 */
public interface IIndexSummaryService {

    /**
     * 获取当前用户的首页总览数据，命中缓存则直接返回。
     */
    IndexSummaryVo getSummary(Long userId);

    /**
     * 定时任务调用：刷新所有已缓存用户的首页数据，避免缓存过期后首请求变慢。
     */
    void refreshAll();
}
