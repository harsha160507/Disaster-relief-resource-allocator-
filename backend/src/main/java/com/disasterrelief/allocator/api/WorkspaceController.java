package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.service.WorkspaceQueryService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceQueryService queries;
    @GetMapping("/overview")
    public Map<String, Long> overview(@RequestParam(required = false) UUID organizationId) {
        return queries.overview(organizationId);
    }
    @GetMapping("/{collection}")
    public WorkspaceQueryService.PageView search(@PathVariable String collection,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID organizationId, @RequestParam(required = false) UUID locationId) {
        return queries.search(collection, page, size, q, status, organizationId, locationId);
    }
}
