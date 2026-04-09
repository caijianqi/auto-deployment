package com.what.deploy.service;

import com.what.deploy.model.JavaProcessInfo;
import java.util.List;

public interface JavaProcessService {
    List<JavaProcessInfo> listProcesses();
    void killProcess(String pid);
}
