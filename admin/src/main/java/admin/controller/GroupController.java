package admin.controller;

import admin.common.biz.user.UserContext;
import admin.common.convention.result.Result;
import admin.common.convention.result.Results;
import admin.dao.entity.GroupDO;
import admin.dto.req.GroupChangeNameReqDTO;
import admin.dto.req.GroupSortOrderUpdateReqDTO;
import admin.dto.req.ShortLinkGroupSaveReqDTO;
import admin.dto.resp.ShortLinkGroupListRespDTO;
import admin.service.GroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group")
@Slf4j
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping("/save")
    public Result<Void> saveGroup (@RequestBody ShortLinkGroupSaveReqDTO shortLinkGroupSaveReqDTO){
        groupService.saveGroup(shortLinkGroupSaveReqDTO.getName());
        return Results.success();
    }

    @GetMapping("/list")
    public Result<List<ShortLinkGroupListRespDTO>> listGroup(){
        return Results.success(groupService.groupList());
    }

    @PutMapping("/updateGroup")
    public Result<String> changeGroupName(@RequestBody GroupChangeNameReqDTO requestParam){
        LambdaQueryWrapper<GroupDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(GroupDO::getUsername, UserContext.getCurrentUser());
        lambdaQueryWrapper.eq(GroupDO::getGid,requestParam.getGid());
        lambdaQueryWrapper.eq(GroupDO::getDelFlag,0);
        GroupDO groupDO = new GroupDO();
        groupDO.setName(requestParam.getName());
        groupService.update(groupDO,lambdaQueryWrapper);
        return Results.success("名称修改成功");
    }

    @DeleteMapping("/deleteGroup")
    public Result<String> deleteGroupByGid(@RequestParam String gid){
        groupService.deleteGroup(gid);
        return Results.success("删除成功");
    }
    @PostMapping("/updateGroupSort")
    public Result<String> updateGroupSortOrderByGid(@RequestBody List<GroupSortOrderUpdateReqDTO> requestParam){
        groupService.updateSortOrder(requestParam);
        return Results.success("更新成功");
    }


}
