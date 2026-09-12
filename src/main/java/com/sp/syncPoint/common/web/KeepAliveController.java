package com.sp.syncPoint.common.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Render's free tier spins the service down after a period with no inbound
 * requests, and the cold start on the next real request can take several
 * seconds. The client pings this every 3 minutes while the app is open
 * (see keepAlive.ts) purely to keep the instance warm.
 *
 * <p>Deliberately does nothing else: no database call, nothing persisted,
 * no auth. A "the server is warm" ping should never itself be the reason
 * the server has anything to do, and it needs to keep working even if the
 * caller has no valid session — see WebSecurityConfig, where this path is
 * permitAll.
 */
@RestController
@RequestMapping("${app.title}")
public class KeepAliveController {

    @GetMapping("/keep-alive")
    public ResponseEntity<String> keepAlive() {
        return ResponseEntity.ok("OK");
    }
}
