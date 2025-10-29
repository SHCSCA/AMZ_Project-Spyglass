package com.amz.spyglass.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器（中文注释）：
 * 提供简单的 Thymeleaf 模板页面，用于快速验证后端是否启动。
 * 真实产品中前端由 AI 生成的 SPA（React/Vue）替换，此页面仅作开发与 smoke-test 使用。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("message", "AMZ Project Spyglass 已启动（skeleton）");
        return "index";
    }
}
