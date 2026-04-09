package com.what.deploy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.what.deploy.entity.DeployLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeployLogMapper extends BaseMapper<DeployLog> {
}
