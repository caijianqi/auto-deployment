package com.what.deploy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文件信息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileItem {
    private String name;
    private String path;
    private Boolean isDir;
    private Long size;
    private Date lastModified;
}
