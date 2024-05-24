package admin.remote.dto.req;

import cn.hutool.db.Page;
import lombok.Data;

import java.util.List;

@Data
public class ShortLinkRecycleBinPageReqDTO extends Page {
    private List<String> gidList;
}
