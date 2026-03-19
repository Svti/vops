package com.vti.vops.dto;

import com.vti.vops.entity.Host;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 主机列表页缓存数据：按用户缓存，定时刷新后打开列表直接使用。
 */
@Data
public class HostListDataVo {
    private List<Host> hosts;
    private Map<Long, HostStatusVo> statusMap;
    private Map<Long, Integer> hostIdToRuleCount;
}
