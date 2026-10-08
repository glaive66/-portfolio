package com.autoops.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OpsWebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
