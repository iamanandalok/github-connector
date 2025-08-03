package com.github_connector.github_connector.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        // this will serve src/main/resources/static/index.html
        return "forward:/index.html";
    }
}
