package com.what.deploy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 路由转发控制器
 * 确保所有非 API、非资源文件的深层路径请求都能正确转发至 index.html
 * 以支持 Vue Router 的 History 模式，防止刷新页面 404
 */
@Controller
public class SpaController {

    @RequestMapping(value = {
        "/{path:[^\\.]*}",
        "/{path:^(?!api|static).*}/{path2:[^\\.]*}",
        "/{path:^(?!api|static).*}/{path2:[^\\.]*}/{path3:[^\\.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
