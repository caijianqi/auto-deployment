package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("nginx_backup")
public class NginxBackup {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String filePath;
    private Long size;
    private String remark;
    private Date createTime;
}
