package org.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller                     // 使用 @Controller 返回視圖
@RequestMapping("/web")
public class WebController {

    @GetMapping("/upload")
    public String showUploadPage() {
        return "upload";  // 對應 templates/upload.html
    }
}