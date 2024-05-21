package project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import project.dao.entity.ShortLinkDO;

@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {
}
