package admin.dto.resp;

import lombok.Data;

@Data
public class ShortLinkGroupListRespDTO {
    /**
     * 组id
     */
    private String gid;

    /**
     * 组名
     */
    private String name;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 短链接数量
     */
    private Integer shortLinkCount;

}
