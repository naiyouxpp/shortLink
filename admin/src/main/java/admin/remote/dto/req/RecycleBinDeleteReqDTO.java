package admin.remote.dto.req;

import lombok.Data;

@Data
public class RecycleBinDeleteReqDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 全部短链接
     */
    private String fullShortUrl;
}
