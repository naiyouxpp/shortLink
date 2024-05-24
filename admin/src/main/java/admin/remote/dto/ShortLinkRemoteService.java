package admin.remote.dto;

import admin.common.convention.result.Result;
import admin.common.convention.result.Results;
import admin.remote.dto.req.*;
import admin.remote.dto.resp.ShortLinkCreateRespDTO;
import admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import admin.remote.dto.resp.ShortLinkPageRespDTO;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ShortLinkRemoteService {

    default Result<ShortLinkCreateRespDTO> createLink(ShortLinkCreateReqDTO requestParam){
        String resultStr = HttpUtil.post("http://127.0.0.1:8001/shortLink/create", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultStr, new TypeReference<>(){});
    }

    default Result<IPage<ShortLinkPageRespDTO>> Page(ShortLinkPageReqDTO requestParam){
        String resultStr = HttpUtil.post("http://localhost:8001/shortLink/getPage", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultStr, new TypeReference<>(){});
    }

    default Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(List<String> requestParam){
        String resultStr = HttpUtil.post("http://localhost:8001/shortLink/count", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultStr, new TypeReference<>(){});
    }
    default Result<Void> shortLinkUpdate(ShortLinkUpdateReqDTO requestParam){
        HttpUtil.post("http://localhost:8001/shortLink/create", JSON.toJSONString(requestParam));
        return Results.success();
    }
    default Result<String> getTitleByUrl(String requestParam){
        Map<String,Object> map = new HashMap<>();
        map.put("url",requestParam);
        String resultStr = HttpUtil.post("http://localhost:8001/shortLink/title", map);
        return JSON.parseObject(resultStr, new TypeReference<>(){});
    }
    default Result<Void> saveRecycleBin(RecycleBinSaveReqDTO requestParam){
        HttpUtil.post("http://localhost:8001/shortLink/save", JSON.toJSONString(requestParam));
        return Results.success();
    }
    default Result<IPage<ShortLinkPageRespDTO>> recycleBinPage(ShortLinkRecycleBinPageReqDTO requestParam){
        String resultStr = HttpUtil.post("http://localhost:8001/shortLink/recycleBinGetPage", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultStr, new TypeReference<>(){});
    }
    default Result<Void> recoverRecycleBin(RecycleBinRecoverReqDTO requestParam){
        HttpUtil.post("http://localhost:8001/shortLink/recoverRecycleBin", JSON.toJSONString(requestParam));
        return Results.success();
    }
    default Result<Void> deleteRecycleBin(RecycleBinDeleteReqDTO requestParam){
        HttpUtil.post("http://localhost:8001/shortLink/deleteRecycleBin", JSON.toJSONString(requestParam));
        return Results.success();
    }
}
