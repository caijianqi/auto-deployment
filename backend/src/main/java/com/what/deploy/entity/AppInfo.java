package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("app_info")
public class AppInfo {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String type;
    private String path;
    private String deployDir; // 部署目录，存放JAR文件的位置
    private String configPath;
    private Integer port;
    private String status; // RUNNING, STOPPED, STARTING
    private Long pid;
    private String configContent;
    private String version;
    private Integer jdkId;
    private Integer isPortEnabled; // 0: 禁用, 1: 启用
    private Date lastDeployTime;
    private Date createTime;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer actualPort;
}
