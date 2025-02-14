package project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import project.dao.entity.ShortLinkDO;
import project.dao.mapper.ShortLinkMapper;
import project.dto.req.RecycleBinDeleteReqDTO;
import project.dto.req.RecycleBinRecoverReqDTO;
import project.dto.req.RecycleBinSaveReqDTO;
import project.dto.req.ShortLinkRecycleBinPageReqDTO;
import project.dto.resp.ShortLinkPageRespDTO;
import project.service.RecycleBinService;

import static project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO>  implements RecycleBinService {


    private final ShortLinkMapper shortLinkMapper ;

    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public Void save(RecycleBinSaveReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ShortLinkDO::getFullShortUrl,requestParam.getFullShortUrl());
        lambdaQueryWrapper.eq(ShortLinkDO::getGid,requestParam.getGid());
        lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,0);
        lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(1)
                .build();
        shortLinkMapper.update(shortLinkDO,lambdaQueryWrapper);

        //删缓存
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY,requestParam.getFullShortUrl()));

        return null;
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(ShortLinkDO::getGid,requestParam.getGidList());
        lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
        lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,1);
        lambdaQueryWrapper.orderByDesc(ShortLinkDO::getUpdateTime);
        IPage<ShortLinkDO> resultPage = shortLinkMapper.selectPage(requestParam, lambdaQueryWrapper);
        return resultPage.convert(each ->{
                    ShortLinkPageRespDTO result =  BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
                    result.setDomain("http://"+result.getDomain());
                    return result;
                }
        );
    }

    @Override
    public Void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ShortLinkDO::getFullShortUrl,requestParam.getFullShortUrl());
        lambdaQueryWrapper.eq(ShortLinkDO::getGid,requestParam.getGid());
        lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,1);
        lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(0)
                .build();
        shortLinkMapper.update(shortLinkDO,lambdaQueryWrapper);

        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY,requestParam.getFullShortUrl()));

        return null;
    }

    @Override
    public Void deleteRecycleBin(RecycleBinDeleteReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ShortLinkDO::getFullShortUrl,requestParam.getFullShortUrl());
        lambdaQueryWrapper.eq(ShortLinkDO::getGid,requestParam.getGid());
        lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,1);
        lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
        shortLinkMapper.delete(lambdaQueryWrapper);
        return null;
    }

}
