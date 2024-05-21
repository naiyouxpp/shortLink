package project.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import project.common.convention.result.Result;
import project.common.convention.result.Results;
import project.service.UrlTitileService;

@RestController
@RequestMapping("/shortLink")
@RequiredArgsConstructor
public class UrlTitleController {

    private final UrlTitileService urlTitileService;

    @PostMapping("/title")
    public Result<String> getTitleByUrl(@RequestParam("url") String url){
        return Results.success(urlTitileService.getTitleByUrl(url)) ;
    }
}
