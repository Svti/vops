package com.vti.vops.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.HostMetric;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 主机指标服务接口
 */
public interface IHostMetricService extends IService<HostMetric> {

    List<HostMetric> listByHostIdAndTimeRange(Long hostId, Date start, Date end);

    /**
     * 监控详情页「近 24 小时」图表专用：只查并返回 collectTime、cpuUsage、memUsagePercent、networkRxRateBps、networkTxRateBps。
     */
    List<HostMetric> listByHostIdAndTimeRangeForChart(Long hostId, Date start, Date end);

    /**
     * 每个主机取最新一条采集记录（无则无该主机）。
     * @param forCardsOnly true 时只查卡片所需列（监控首页用），false 时查全列（详情页/主机列表等用）。
     */
    List<HostMetric> listLatestByHostIds(List<Long> hostIds, boolean forCardsOnly);

    /**
     * 分页查询采集记录，支持按主机筛选和排序。
     * @param hostId 可选，指定则只查该主机
     * @param current 页码从 1 开始
     * @param size 每页条数
     * @param orderBy 排序字段：collectTime / cpuUsage / memUsagePercent / diskUsagePercent
     * @param asc 是否升序
     */
    IPage<HostMetric> pageMetrics(Long hostId, long current, long size, String orderBy, boolean asc);

    /**
     * 删除采集时间早于指定日期的记录，用于定时清理历史数据。
     * @param before 早于此时间的记录将被删除
     * @return 删除条数
     */
    int deleteOlderThan(Date before);

    /**
     * 按主机取最新一条采集记录（用于 ICMP 更新同一行或告警评估）。
     */
    Optional<HostMetric> getLatestByHostId(Long hostId);

    /**
     * 更新该主机最新一条记录的 ICMP 时延；若无记录则插入仅含 hostId/collectTime/icmpRttMs 的 minimal 行。
     * 不可达时传 HostMetric.ICMP_UNREACHABLE_RTT_MS（9999）。
     */
    void saveOrUpdateIcmp(Long hostId, Date collectTime, Long icmpRttMs);
}
