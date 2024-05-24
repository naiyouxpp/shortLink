package admin.service.serviceImpl;

import admin.common.biz.user.UserContext;
import admin.common.convention.exception.ServiceException;
import admin.common.convention.result.Result;
import admin.dao.entity.GroupDO;
import admin.dao.mapper.GroupMapper;
import admin.remote.dto.ShortLinkRemoteService;
import admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import admin.remote.dto.resp.ShortLinkPageRespDTO;
import admin.service.RecycleBinService;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final GroupMapper groupMapper;
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };
    @Override
    public Result<IPage<ShortLinkPageRespDTO>> recycleBinPage(ShortLinkRecycleBinPageReqDTO requestParam) {
        LambdaQueryWrapper<GroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(GroupDO::getUsername, UserContext.getCurrentUser());
        lambdaQueryWrapper.eq(GroupDO::getDelFlag,0);

        List<GroupDO> groupDOList = groupMapper.selectList(lambdaQueryWrapper);
        if (CollUtil.isEmpty(groupDOList)){
            throw new ServiceException("用户无分组信息");
        }
        requestParam.setGidList(groupDOList.stream().map(GroupDO::getGid).toList());

        return shortLinkRemoteService.recycleBinPage(requestParam);
    }
}
