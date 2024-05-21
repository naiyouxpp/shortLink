package project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.jsoup.Jsoup;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.common.convention.exception.ServiceException;
import project.dao.entity.ShortLinkDO;
import project.dao.entity.ShortLinkGotoDO;
import project.dao.mapper.ShortLinkGotoMapper;
import project.dao.mapper.ShortLinkMapper;
import project.dto.req.ShortLinkCreateReqDTO;
import project.dto.req.ShortLinkPageReqDTO;
import project.dto.req.ShortLinkUpdateReqDTO;
import project.dto.resp.ShortLinkCreateRespDTO;
import project.dto.resp.ShortLinkGroupCountRespDTO;
import project.dto.resp.ShortLinkPageRespDTO;
import project.service.ShortLinkService;
import project.toolkit.HashUtil;
import project.toolkit.LinkUtil;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static project.common.constant.RedisKeyConstant.*;
import static project.common.enums.VailDateTypeEnum.PERMANENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final RBloomFilter<String> shortLinkCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        String suffix = createSuffix(requestParam);
        String fullShortUrl = "http://"+requestParam.getDomain() + "/" + suffix;

        ShortLinkDO shortLinkDO = BeanUtil.copyProperties(requestParam, ShortLinkDO.class);
        shortLinkDO.setShortUri(suffix);
        shortLinkDO.setEnableStatus(0);
        shortLinkDO.setFullShortUrl(fullShortUrl);
            // 固定前缀
             String newUrl = "http://localhost:8001/shortLink/"+suffix;
        shortLinkDO.setFavicon(getFavicon(newUrl));//记得来改fullShortLink

        ShortLinkGotoDO gotoDO = ShortLinkGotoDO.builder()
                .fullShortLink(fullShortUrl)
                .gid(requestParam.getGid())
                .build();

        //有可能会重复入库
        try {
            shortLinkMapper.insert(shortLinkDO);
            shortLinkGotoMapper.insert(gotoDO);
        } catch (DuplicateKeyException e) {
            LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO hasShortLinkDO = shortLinkMapper.selectOne(lambdaQueryWrapper);
            if (hasShortLinkDO != null) {
                log.warn("重复入库");
                throw new ServiceException("短链接生成重复");
            }
        }
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY,fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()),TimeUnit.MICROSECONDS
        );
        shortLinkCreateCachePenetrationBloomFilter.add(fullShortUrl);
        return ShortLinkCreateRespDTO.builder()
                .gid(requestParam.getGid())
                .originUrl(requestParam.getOriginUrl())
                .fullShortUrl(shortLinkDO.getFullShortUrl())
                .build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ShortLinkDO::getGid,requestParam.getGid());
        lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
        lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,0);
        IPage<ShortLinkDO> resultPage = shortLinkMapper.selectPage(requestParam, lambdaQueryWrapper);
        return resultPage.convert(each ->{
                    ShortLinkPageRespDTO result =  BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
                    result.setDomain("http://"+result.getDomain());
                    return result;
                }
            );
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> countShortLink(List<String> requestParam) {
        log.info("接收到的gids列表:"+requestParam.toString());
        List<ShortLinkGroupCountRespDTO> collect = requestParam.stream().map(each -> {
            ShortLinkGroupCountRespDTO shortLinkGroupCountRespDTO = new ShortLinkGroupCountRespDTO();
            LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus, 0);
            lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag, 0);
            lambdaQueryWrapper.eq(ShortLinkDO::getGid, each);
            Integer count = shortLinkMapper.selectCount(lambdaQueryWrapper).intValue();;
            shortLinkGroupCountRespDTO.setGid(each);
            shortLinkGroupCountRespDTO.setShortLinkCount(count);
            return shortLinkGroupCountRespDTO;
        }).collect(Collectors.toList());
        return collect;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void shortLinkUpdate(ShortLinkUpdateReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = lambdaQueryWrapper
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO resultShortLink = shortLinkMapper.selectOne(queryWrapper);
        if (resultShortLink==null){
            throw new ServiceException("短链接记录不存在");
        }

        //如果没有换分组
        if (Objects.equals(resultShortLink.getGid(),requestParam.getGid())){

            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(Objects.equals(PERMANENT.getType(), requestParam.getValidDateType()), ShortLinkDO::getValidDate, null);
            ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                    .originUrl(requestParam.getOriginUrl())
                    .gid(requestParam.getGid())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    .describe(requestParam.getDescribe())
                    .domain(resultShortLink.getDomain())
                    .shortUri(resultShortLink.getShortUri())
                    .favicon(resultShortLink.getFavicon())
                    .createdType(resultShortLink.getCreatedType())
                    .build();
            shortLinkMapper.update(shortLinkDO,updateWrapper);
        }else {
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, resultShortLink.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(ShortLinkDO::getDelFlag,1);
            shortLinkMapper.update(resultShortLink,updateWrapper);

            ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                    .originUrl(requestParam.getOriginUrl())
                    .gid(requestParam.getGid())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    .describe(requestParam.getDescribe())
                    .domain(resultShortLink.getDomain())
                    .shortUri(resultShortLink.getShortUri())
                    .favicon(resultShortLink.getFavicon())
                    .createdType(resultShortLink.getCreatedType())
                    .enableStatus(resultShortLink.getEnableStatus())
                    .fullShortUrl(resultShortLink.getFullShortUrl())
                    .build();
            shortLinkMapper.insert(shortLinkDO);

            LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = new LambdaQueryWrapper<>();
            linkGotoQueryWrapper.eq(ShortLinkGotoDO::getFullShortLink,requestParam.getFullShortUrl());
            linkGotoQueryWrapper.eq(ShortLinkGotoDO::getGid,resultShortLink.getGid());
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
            shortLinkGotoMapper.delete(linkGotoQueryWrapper);
            shortLinkGotoDO.setGid(requestParam.getGid());
            shortLinkGotoMapper.insert(shortLinkGotoDO);
        }
    }

    @SneakyThrows
    @Override
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) {

        String fullShortLink  = "http://localhost" +"/"+ shortUri;
        String originUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortLink));

        if (StrUtil.isNotBlank(originUrl)){
            ((HttpServletResponse)response).sendRedirect(originUrl);
            return ;
        }
        boolean contains = shortLinkCreateCachePenetrationBloomFilter.contains(fullShortLink);

        if (!contains){
            ((HttpServletResponse)response).sendRedirect("/page/notfound");
            return;
        }
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortLink));
        if (StrUtil.isNotBlank(gotoIsNullShortLink)){
            ((HttpServletResponse)response).sendRedirect("/page/notfound");
            return;
        }

        RLock lock = redissonClient.getLock(StrUtil.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortLink));
        lock.lock();
        try {
             originUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortLink));
            if (StrUtil.isNotBlank(originUrl)){
                ((HttpServletResponse)response).sendRedirect(originUrl);
                return ;
            }

            LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ShortLinkGotoDO::getFullShortLink,fullShortLink);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
            if(shortLinkGotoDO==null){
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortLink),"-",30, TimeUnit.MINUTES);
                ((HttpServletResponse)response).sendRedirect("/page/notfound");
                return;
            }
            LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper  = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(ShortLinkDO::getFullShortUrl,fullShortLink);
            lambdaQueryWrapper.eq(ShortLinkDO::getGid,shortLinkGotoDO.getGid());
            lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
            lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,0);
            ShortLinkDO shortLinkDO = shortLinkMapper.selectOne(lambdaQueryWrapper);

            if (shortLinkDO!=null) {
                if (shortLinkDO.getValidDate()!=null&&shortLinkDO.getValidDate().before(new Date())){
                    stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortLink),"-",30, TimeUnit.MINUTES);
                    ((HttpServletResponse)response).sendRedirect("/page/notfound");
                    return;
                }
                stringRedisTemplate.opsForValue().set(
                        String.format(GOTO_SHORT_LINK_KEY,fullShortLink),
                        shortLinkDO.getOriginUrl(),
                        LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()),TimeUnit.MICROSECONDS
                );
                ((HttpServletResponse)response).sendRedirect(shortLinkDO.getOriginUrl());
            }
        }finally {
            lock.unlock();
        }


    }

    public String createSuffix(ShortLinkCreateReqDTO requestParam) {
        int count = 1;
        String shortUri;
        while (true) {
            if (count >= 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            //生成短链接后六位
            //防止hash生成发生冲突，也就是两个不同的url却生成了同个短链接，
            //hash生成如果两个string差距越大，生成出相同的短链接概率越低
            String originUrl = requestParam.getOriginUrl();
            originUrl += System.currentTimeMillis();
            shortUri = HashUtil.hashToBase62(originUrl);
            if (!shortLinkCreateCachePenetrationBloomFilter.contains(requestParam.getDomain() + "/" + shortUri)) {
                break;
            }
            count++;
        }
        return shortUri;
    }
    @SneakyThrows
    public String getFavicon(String url){
        URL target = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode==HttpURLConnection.HTTP_OK){
            Document document = Jsoup.connect(url).get();
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            if (faviconLink != null){
                return  faviconLink.attr("abs:href");
            }


        }
        return null;
    }

}
