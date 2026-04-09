package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_template")
public class SystemTemplate {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String content;
}
