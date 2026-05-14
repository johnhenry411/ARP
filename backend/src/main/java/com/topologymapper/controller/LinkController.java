package com.topologymapper.controller;

import com.topologymapper.dto.AddLinkRequest;
import com.topologymapper.model.Link;
import com.topologymapper.service.TopologyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    @Autowired
    private TopologyService topologyService;

    @GetMapping
    public Collection<Link> getAll() {
        return topologyService.getAllLinks();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Link addLink(@RequestBody @Valid AddLinkRequest req) {
        return topologyService.addLink(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLink(@PathVariable String id) {
        topologyService.removeLink(id);
    }
}
