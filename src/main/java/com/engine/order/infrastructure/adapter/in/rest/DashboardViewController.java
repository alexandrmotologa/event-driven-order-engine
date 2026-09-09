package com.engine.order.infrastructure.adapter.in.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardViewController {

    @GetMapping({"/dashboard", "/"})
    public String viewDashboard() {
        return "forward:/dashboard/index.html";
    }
}
