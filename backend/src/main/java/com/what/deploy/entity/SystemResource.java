package com.what.deploy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_resource")
public class SystemResource {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String path;
    private String type;
    private String category;
    private Integer isUsed;
    private String configContent;
    private String remark;
}
