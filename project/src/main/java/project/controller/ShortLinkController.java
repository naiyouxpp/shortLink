package project.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import project.common.convention.result.Result;
import project.common.convention.result.Results;
import project.dto.req.ShortLinkCreateReqDTO;
import project.dto.req.ShortLinkPageReqDTO;
import project.dto.req.ShortLinkUpdateReqDTO;
import project.dto.resp.ShortLinkCreateRespDTO;
import project.dto.resp.ShortLinkGroupCountRespDTO;
import project.dto.resp.ShortLinkPageRespDTO;
import project.service.ShortLinkService;

import java.util.List;


@Slf4j
@RequestMapping("/shortLink")
@RequiredArgsConstructor
@RestController
public class ShortLinkController {
    private final ShortLinkService shortLinkService;

    @PostMapping("/create")
    public Result<ShortLinkCreateRespDTO> createLink(@RequestBody ShortLinkCreateReqDTO requestParam){
        return Results.success(shortLinkService.createShortLink(requestParam));
    }
    @PostMapping("/getPage")
    public Result<IPage<ShortLinkPageRespDTO>> Page(@RequestBody ShortLinkPageReqDTO requestParam){
        return Results.success(shortLinkService.pageShortLink(requestParam));
    }
    @PostMapping("/count")
    public Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(@RequestBody List<String> requestParam){
        return Results.success(shortLinkService.countShortLink(requestParam));
    }
    @PostMapping("/update")
    public Result<Void> shortLinkUpdate(@RequestBody ShortLinkUpdateReqDTO requestParam){
        shortLinkService.shortLinkUpdate(requestParam);
        return Results.success();
    }
    @GetMapping("/{short-uri}")
    public Void restoreUrl(@PathVariable("short-uri") String shortUri, ServletRequest request, ServletResponse response){
        shortLinkService.restoreUrl(shortUri,request,response);
        return null;
    }
}
