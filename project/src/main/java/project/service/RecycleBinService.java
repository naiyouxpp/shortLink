package project.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import project.dao.entity.ShortLinkDO;
import project.dto.req.RecycleBinDeleteReqDTO;
import project.dto.req.RecycleBinRecoverReqDTO;
import project.dto.req.RecycleBinSaveReqDTO;
import project.dto.req.ShortLinkRecycleBinPageReqDTO;
import project.dto.resp.ShortLinkPageRespDTO;

public interface RecycleBinService extends IService<ShortLinkDO> {

    Void save(RecycleBinSaveReqDTO requestParam);

    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam);

    Void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam);

    Void deleteRecycleBin(RecycleBinDeleteReqDTO requestParam);
}
