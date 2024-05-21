package admin.dao.entity;

import admin.common.database.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("t_group")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupDO extends BaseDO {
    private Long id;

    /**
     * 组id
     */
    private String gid;

    /**
     * 组名
     */
    private String name;

    /**
     * 创建人姓名
     */
    private String username;

    /**
     * 排序
     */
    private Integer sortOrder;
}
