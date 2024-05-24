package admin.service;

import admin.common.convention.result.Result;
import admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
public interface RecycleBinService {
    Result<IPage<ShortLinkPageRespDTO>> recycleBinPage(ShortLinkRecycleBinPageReqDTO requestParam);
}
