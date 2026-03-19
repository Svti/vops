package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.mapper.HostMetricMapper;
import com.vti.vops.service.IHostMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HostMetricServiceImpl extends ServiceImpl<HostMetricMapper, HostMetric> implements IHostMetricService {

    @Override
    public List<HostMetric> listByHostIdAndTimeRange(Long hostId, Date start, Date end) {
        return list(new LambdaQueryWrapper<HostMetric>()
                .eq(HostMetric::getHostId, hostId)
                .ge(HostMetric::getCollectTime, start)
                .le(HostMetric::getCollectTime, end)
                .orderByAsc(HostMetric::getCollectTime));
    }

    @Override
    public List<HostMetric> listByHostIdAndTimeRangeForChart(Long hostId, Date start, Date end) {
        return list(new LambdaQueryWrapper<HostMetric>()
                .select(HostMetric::getCollectTime, HostMetric::getCpuUsage, HostMetric::getMemUsagePercent,
                        HostMetric::getNetworkRxRateBps, HostMetric::getNetworkTxRateBps)
                .eq(HostMetric::getHostId, hostId)
                .ge(HostMetric::getCollectTime, start)
                .le(HostMetric::getCollectTime, end)
                .orderByAsc(HostMetric::getCollectTime));
    }

    @Override
    public List<HostMetric> listLatestByHostIds(List<Long> hostIds, boolean forCardsOnly) {
        if (hostIds == null || hostIds.isEmpty()) return List.of();
        return hostIds.parallelStream()
                .flatMap(hostId -> {
                    var q = new LambdaQueryWrapper<HostMetric>()
                            .eq(HostMetric::getHostId, hostId)
                            .orderByDesc(HostMetric::getCollectTime)
                            .last("LIMIT 1");
                    if (forCardsOnly) {
                        q.select(HostMetric::getHostId, HostMetric::getCollectTime, HostMetric::getCpuUsage, HostMetric::getCpuCores,
                                HostMetric::getMemTotal, HostMetric::getMemUsagePercent, HostMetric::getDiskTotal, HostMetric::getDiskUsagePercent,
                                HostMetric::getLoadAvg, HostMetric::getNetworkRxRateBps, HostMetric::getNetworkTxRateBps,
                                HostMetric::getIcmpRttMs);
                    }
                    return list(q).stream();
                })
                .collect(Collectors.toList());
    }

    @Override
    public IPage<HostMetric> pageMetrics(Long hostId, long current, long size, String orderBy, boolean asc) {
        LambdaQueryWrapper<HostMetric> q = new LambdaQueryWrapper<HostMetric>();
        if (hostId != null && hostId > 0) {
            q.eq(HostMetric::getHostId, hostId);
        }
        String order = (orderBy != null && !orderBy.isEmpty()) ? orderBy : "collectTime";
        switch (order) {
            case "cpuUsage":
                q.orderBy(true, asc, HostMetric::getCpuUsage);
                break;
            case "memUsagePercent":
                q.orderBy(true, asc, HostMetric::getMemUsagePercent);
                break;
            case "diskUsagePercent":
                q.orderBy(true, asc, HostMetric::getDiskUsagePercent);
                break;
            default:
                q.orderBy(true, asc, HostMetric::getCollectTime);
        }
        return page(new Page<>(current, size), q);
    }

    @Override
    public int deleteOlderThan(Date before) {
        if (before == null) return 0;
        return getBaseMapper().delete(new LambdaQueryWrapper<HostMetric>().lt(HostMetric::getCollectTime, before));
    }

    @Override
    public Optional<HostMetric> getLatestByHostId(Long hostId) {
        if (hostId == null) return Optional.empty();
        List<HostMetric> one = list(new LambdaQueryWrapper<HostMetric>()
                .eq(HostMetric::getHostId, hostId)
                .orderByDesc(HostMetric::getCollectTime)
                .last("LIMIT 1"));
        return one.isEmpty() ? Optional.empty() : Optional.of(one.get(0));
    }

    @Override
    public void saveOrUpdateIcmp(Long hostId, Date collectTime, Long icmpRttMs) {
        if (hostId == null) return;
        long rtt = icmpRttMs != null ? icmpRttMs : HostMetric.ICMP_UNREACHABLE_RTT_MS;
        Date time = collectTime != null ? collectTime : new Date();
        Optional<HostMetric> latest = getLatestByHostId(hostId);
        if (latest.isPresent()) {
            Long id = latest.get().getId();
            if (id != null) {
                update(new LambdaUpdateWrapper<HostMetric>()
                        .eq(HostMetric::getId, id)
                        .set(HostMetric::getIcmpRttMs, rtt)
                        .set(HostMetric::getCollectTime, time));
            }
        } else {
            HostMetric m = new HostMetric();
            m.setHostId(hostId);
            m.setCollectTime(time);
            m.setIcmpRttMs(rtt);
            save(m);
        }
    }
}
