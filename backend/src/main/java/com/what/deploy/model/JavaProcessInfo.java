package com.what.deploy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JavaProcessInfo {
    private String pid;
    private String mainClass;
    private String arguments;
    private boolean isCurrentApp;
}
