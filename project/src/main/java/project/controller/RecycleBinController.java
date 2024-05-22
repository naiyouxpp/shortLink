package project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.common.convention.result.Result;
import project.common.convention.result.Results;
import project.dto.req.RecycleBinSaveReqDTO;
import project.dto.req.ShortLinkPageReqDTO;
import project.dto.resp.ShortLinkPageRespDTO;
import project.service.RecycleBinService;

@RestController
@RequestMapping("/shortLink")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    @PostMapping("/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam){
        return Results.success(recycleBinService.save(requestParam));
    }
    @PostMapping("/recycleBinGetPage")
    public Result<IPage<ShortLinkPageRespDTO>> Page(@RequestBody ShortLinkPageReqDTO requestParam){
        return Results.success(recycleBinService.pageShortLink(requestParam));
    }
}
