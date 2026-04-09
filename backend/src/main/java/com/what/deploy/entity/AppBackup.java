package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("app_backup")
public class AppBackup {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer appId;
    private String version;
    private String filePath;
    private Long size; // bytes
    private Date createTime;
}
