package admin.service;

import admin.dao.entity.GroupDO;
import admin.dto.req.GroupSortOrderUpdateReqDTO;
import admin.dto.resp.ShortLinkGroupListRespDTO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface GroupService extends IService<GroupDO> {
    void saveGroup (String groupName);

    void saveGroup(String username, String groupName);

    List<ShortLinkGroupListRespDTO> groupList();

    void deleteGroup(String gid);

    void updateSortOrder(List<GroupSortOrderUpdateReqDTO> requestParam);
}
