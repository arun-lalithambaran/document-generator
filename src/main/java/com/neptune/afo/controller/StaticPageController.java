package com.neptune.afo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
public class StaticPageController {

  @GetMapping("app/index")
  public String index() {
    return "index.html";
  }
}
