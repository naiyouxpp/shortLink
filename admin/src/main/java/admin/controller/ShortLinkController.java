package admin.controller;

import admin.common.convention.result.Result;
import admin.remote.dto.ShortLinkRemoteService;
import admin.remote.dto.req.*;
import admin.remote.dto.resp.ShortLinkCreateRespDTO;
import admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import admin.remote.dto.resp.ShortLinkPageRespDTO;
import admin.service.RecycleBinService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/shortLink")
@RequiredArgsConstructor
public class ShortLinkController {
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };

    private  final RecycleBinService recycleBinService;
    @PostMapping("/create")
    public Result<ShortLinkCreateRespDTO> createLink(@RequestBody ShortLinkCreateReqDTO requestParam){
        return shortLinkRemoteService.createLink(requestParam);
    }

    @PostMapping("/getPage")
    public Result<IPage<ShortLinkPageRespDTO>> Page(@RequestBody ShortLinkPageReqDTO requestParam){
        return shortLinkRemoteService.Page(requestParam);
    }

    //这个接口有问题，不能用，只能直接去中台调用（8001），不能走8002，应该是数据传输的时候序列化的问题
    @PostMapping("/update")
    public Result<Void> shortLinkUpdate(@RequestBody ShortLinkUpdateReqDTO requestParam){
        return shortLinkRemoteService.shortLinkUpdate(requestParam);
    }
    @PostMapping("/count")
    public Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(@RequestBody List<String> requestParam){
        return shortLinkRemoteService.listGroupShortLinkCount(requestParam);
    }
    //获取原始链接title
    @GetMapping("/title")
    public Result<String> getTitleByUrl(@RequestParam("url") String url){
        return shortLinkRemoteService.getTitleByUrl(url) ;
    }
    @PostMapping("/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam){
        return shortLinkRemoteService.saveRecycleBin(requestParam) ;
    }
    @PostMapping("/recycleBinGetPage")
    public Result<IPage<ShortLinkPageRespDTO>> recycleBinPage(@RequestBody ShortLinkRecycleBinPageReqDTO requestParam){
        return recycleBinService.recycleBinPage(requestParam);
    }
    @PostMapping("/recoverRecycleBin")
    public Result<Void> recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO requestParam){
        return shortLinkRemoteService.recoverRecycleBin(requestParam);
    }
    @PostMapping("/deleteRecycleBin")
    public Result<Void> deleteRecycleBin(@RequestBody RecycleBinDeleteReqDTO requestParam){
        return shortLinkRemoteService.deleteRecycleBin(requestParam);
    }

}
