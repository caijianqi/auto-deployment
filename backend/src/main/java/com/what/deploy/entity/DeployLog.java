package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("deploy_log")
public class DeployLog {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer appId;
    private String opType; // DEPLOY, UPDATE, START, STOP, ROLLBACK
    private String content;
    private Date createTime;
}
