package admin.service.serviceImpl;

import admin.common.biz.user.UserContext;
import admin.common.convention.result.Result;
import admin.dao.entity.GroupDO;
import admin.dao.mapper.GroupMapper;
import admin.dto.req.GroupSortOrderUpdateReqDTO;
import admin.dto.resp.ShortLinkGroupListRespDTO;
import admin.remote.dto.ShortLinkRemoteService;
import admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import admin.service.GroupService;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };

    private final GroupMapper groupMapper;

    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getCurrentUser(),groupName);
    }


    @Override
    public void saveGroup(String username, String groupName) {
        String gid;
        while (true) {
            gid = RandomUtil.randomString(6);
            if (!hasGid(username,gid)) {
                break;
            }
        }
        GroupDO groupDO = GroupDO.builder()
                .gid(gid)
                .username(username)
                .name(groupName)
                .sortOrder(0)
                .build();
        groupMapper.insert(groupDO);
        return;
    }

    @Override
    public List<ShortLinkGroupListRespDTO> groupList() {
        LambdaQueryWrapper<GroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(GroupDO::getDelFlag, 0);
        lambdaQueryWrapper.eq(GroupDO::getUsername, UserContext.getCurrentUser());
        lambdaQueryWrapper.orderByDesc(GroupDO::getSortOrder, GroupDO::getCreateTime);
        //使用用户名
        List<GroupDO> groupDOS = groupMapper.selectList(lambdaQueryWrapper);

        //1.拿到当前组下的点击数量和但前组gid
        Result<List<ShortLinkGroupCountRespDTO>> listResult = shortLinkRemoteService.listGroupShortLinkCount(
                groupDOS.stream().map(each -> {
                    return each.getGid();
                }).collect(Collectors.toList())

        );
        //2.拿到当前用户的所有组，并将其转换成ShortLinkGroupListRespDTO类，里面记录了当前用户下
        //每个组中关于组名，排序，当前组下短链接数量的记录，但是记录需要自己加
        List<ShortLinkGroupListRespDTO> shortLinkGroupListRespDTOS = BeanUtil.copyToList(groupDOS, ShortLinkGroupListRespDTO.class);

        //3.将一和二融合
        shortLinkGroupListRespDTOS.forEach(each -> {
            Optional<ShortLinkGroupCountRespDTO> optionalCountRespDTO = listResult.getData().stream()
                    .filter(item -> each.getGid().equals(item.getGid()))
                    .findFirst();

            optionalCountRespDTO.ifPresent(countRespDTO -> each.setShortLinkCount(countRespDTO.getShortLinkCount()));
        });



        return shortLinkGroupListRespDTOS;
    }

    @Override
    public void deleteGroup(String gid) {
        LambdaQueryWrapper<GroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(GroupDO::getGid, gid);
        lambdaQueryWrapper.eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);
        groupMapper.update(groupDO, lambdaQueryWrapper);
    }

    @Override
    public void updateSortOrder(List<GroupSortOrderUpdateReqDTO> requestParam) {
        List<GroupDO> groupDOS = BeanUtil.copyToList(requestParam, GroupDO.class);

        groupDOS.stream().map(item -> {
            // LambdaQueryWrapper不可new在stream外面，不会刷新的，每次都要重新new才行
            LambdaQueryWrapper<GroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(GroupDO::getDelFlag, 0);
            lambdaQueryWrapper.eq(GroupDO::getUsername, UserContext.getCurrentUser());
            lambdaQueryWrapper.eq(GroupDO::getGid, item.getGid());
            groupMapper.update(item, lambdaQueryWrapper);
            return null;
        }).collect(Collectors.toList());

    }

    private Boolean hasGid(String username,String gid) {
        LambdaQueryWrapper<GroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(GroupDO::getGid, gid);
        lambdaQueryWrapper.eq(GroupDO::getUsername, Optional.ofNullable(username).orElse(UserContext.getCurrentUser()));
        GroupDO groupDO = groupMapper.selectOne(lambdaQueryWrapper);
        return groupDO != null;
    }
}
