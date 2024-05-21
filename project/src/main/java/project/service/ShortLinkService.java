package project.service;

import cn.hutool.http.server.HttpServerRequest;
import cn.hutool.http.server.HttpServerResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import project.dao.entity.ShortLinkDO;
import project.dto.req.ShortLinkCreateReqDTO;
import project.dto.req.ShortLinkPageReqDTO;
import project.dto.req.ShortLinkUpdateReqDTO;
import project.dto.resp.ShortLinkCreateRespDTO;
import project.dto.resp.ShortLinkGroupCountRespDTO;
import project.dto.resp.ShortLinkPageRespDTO;

import java.util.List;

public interface ShortLinkService extends IService<ShortLinkDO> {
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    List<ShortLinkGroupCountRespDTO> countShortLink(List<String> requestParam);

    void shortLinkUpdate(ShortLinkUpdateReqDTO requestParam);

    void restoreUrl(String shortUri, ServletRequest request, ServletResponse response);
}
