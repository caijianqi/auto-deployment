package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_jdk")
public class SystemJdk {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String path;
    private Date createTime;
}
