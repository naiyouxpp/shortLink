package project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.jsoup.Jsoup;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.common.convention.exception.ServiceException;
import project.dao.entity.*;
import project.dao.mapper.*;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static project.common.constant.RedisKeyConstant.*;
import static project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;
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
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    @Value("${short-Link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        String suffix = createSuffix(requestParam);
        String fullShortUrl = "http://"+requestParam.getDomain() + "/" + suffix;

        ShortLinkDO shortLinkDO = BeanUtil.copyProperties(requestParam, ShortLinkDO.class);
        shortLinkDO.setShortUri(suffix);
        shortLinkDO.setEnableStatus(0);
        shortLinkDO.setFullShortUrl(fullShortUrl);
        shortLinkDO.setTotalPv(0);
        shortLinkDO.setTotalUv(0);
        shortLinkDO.setTotalUip(0);
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
        IPage<ShortLinkDO> resultPage = shortLinkMapper.pageLink(requestParam);
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
            Integer count = shortLinkMapper.selectCount(lambdaQueryWrapper).intValue();
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
        String fullShortLink  = "http://localhost/"+ shortUri;
        String originUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortLink));

        if (StrUtil.isNotBlank(originUrl)){
            shortLinkStats(fullShortLink,null,request,response);
            ((HttpServletResponse)response).sendRedirect(originUrl);
            return ;
        }
        boolean contains = shortLinkCreateCachePenetrationBloomFilter.contains(fullShortLink);

        if (!contains){
            shortLinkStats(fullShortLink,null,request,response);
            ((HttpServletResponse)response).sendRedirect("/page/notfound");
            return;
        }
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortLink));
        if (StrUtil.isNotBlank(gotoIsNullShortLink)){
            shortLinkStats(fullShortLink,null,request,response);
            ((HttpServletResponse)response).sendRedirect("/page/notfound");
            return;
        }
        RLock lock = redissonClient.getLock(StrUtil.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortLink));
        lock.lock();
        try {
             originUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortLink));
            if (StrUtil.isNotBlank(originUrl)){
                shortLinkStats(fullShortLink,null,request,response);
                ((HttpServletResponse)response).sendRedirect(originUrl);
                return ;
            }

            LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ShortLinkGotoDO::getFullShortLink,fullShortLink);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
            if(shortLinkGotoDO==null){
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortLink),"-",30, TimeUnit.MINUTES);
                shortLinkStats(fullShortLink,null,request,response);
                ((HttpServletResponse)response).sendRedirect("/page/notfound");
                return;
            }
            LambdaQueryWrapper<ShortLinkDO> lambdaQueryWrapper  = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(ShortLinkDO::getFullShortUrl,fullShortLink);
            lambdaQueryWrapper.eq(ShortLinkDO::getGid,shortLinkGotoDO.getGid());
            lambdaQueryWrapper.eq(ShortLinkDO::getDelFlag,0);
            lambdaQueryWrapper.eq(ShortLinkDO::getEnableStatus,0);
            ShortLinkDO shortLinkDO = shortLinkMapper.selectOne(lambdaQueryWrapper);

            if (shortLinkDO!=null&&shortLinkDO.getValidDate().before(new Date())) {
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortLink),"-",30, TimeUnit.MINUTES);
                shortLinkStats(fullShortLink,shortLinkDO.getGid(),request,response);
                ((HttpServletResponse)response).sendRedirect("/page/notfound");
                return;
            }
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY,fullShortLink),
                    shortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()),TimeUnit.MICROSECONDS
            );
            shortLinkStats(fullShortLink, shortLinkDO.getGid(), request,response);
            ((HttpServletResponse)response).sendRedirect(shortLinkDO.getOriginUrl());
        }finally {
            lock.unlock();
        }
    }
    public void shortLinkStats(String fullShortUrl,String gid,ServletRequest request,ServletResponse response){
        AtomicBoolean uvFistFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        try{
            AtomicReference<String> uv = new AtomicReference<>();
            Runnable addResponseCookieTask = ()->{
               uv.set(UUID.fastUUID().toString());
               Cookie uvCookie = new Cookie("uv",uv.get());
                uvCookie.setMaxAge( 60 * 60 * 24 * 30 );
                uvCookie.setPath(StrUtil.sub(fullShortUrl,fullShortUrl.indexOf("/",fullShortUrl.indexOf("://")+3),fullShortUrl.length()));
                ((HttpServletResponse)response).addCookie(uvCookie);
                uvFistFlag.set(Boolean.TRUE);
                stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, uv.get());
            };
            if (ArrayUtil.isNotEmpty(cookies)){
                Arrays.stream(cookies)
                        .filter(each->Objects.equals(each.getName(),"uv"))
                        .findFirst()
                        .map(Cookie::getValue)
                        .ifPresentOrElse(each->{
                            uv.set(each);
                            Long added = stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, each);
                            uvFistFlag.set(added != null && added > 0L);
                        },addResponseCookieTask);
            }else {
                addResponseCookieTask.run();
            }
            String remoteAddr = LinkUtil.getActualIp((HttpServletRequest) request);
            Long uipAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uip:" + fullShortUrl, remoteAddr);
            boolean uipFristFlag = uipAdded != null && uipAdded > 0L;
            if (StringUtils.isBlank(gid)){
                LambdaQueryWrapper<ShortLinkGotoDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                lambdaQueryWrapper.eq(ShortLinkGotoDO::getFullShortLink,fullShortUrl);
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(lambdaQueryWrapper);
                gid = shortLinkGotoDO.getGid();
            }
            int hour = DateUtil.hour(new Date(), true);
            Week week = DateUtil.dayOfWeekEnum(new Date());
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(uvFistFlag.get() ? 1 : 0)
                    .uip(uipFristFlag ? 1:0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(new Date())
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
            Map<String,Object> map  = new HashMap<>();
            map.put("ip",remoteAddr);
            map.put("key",statsLocaleAmapKey);
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, map);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            String infocode = localeResultObj.getString("infocode");
            String province = localeResultObj.getString("province");
            String actualCity = null;
            String actualProvince = null;
            if (infocode != null && Objects.equals("10000",infocode) ){
                boolean unknownFlag = StrUtil.equals(province,"[]");
                LinkLocaleStatsDO localeStats;
                localeStats = LinkLocaleStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(new Date())
                        .cnt(1)
                        .province(actualProvince = unknownFlag ? "未知" : province)
                        .city(actualCity = unknownFlag ? "未知" : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                        .country("中国")
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(localeStats);
            }
            String os = LinkUtil.getOs((HttpServletRequest) request);
            LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .date(new Date())
                    .gid(gid)
                    .cnt(1)
                    .os(os)
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);

            String browser = LinkUtil.getBrowser((HttpServletRequest) request);
            LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .date(new Date())
                    .gid(gid)
                    .cnt(1)
                    .browser(browser)
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

            String device = LinkUtil.getDevice((HttpServletRequest) request);
            LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .date(new Date())
                    .cnt(1)
                    .device(device)
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
            String network = LinkUtil.getNetwork((HttpServletRequest) request);
            LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(new Date())
                    .cnt(1)
                    .network(network)
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);

            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .user(uv.get())
                    .browser(browser)
                    .os(os)
                    .ip(remoteAddr)
                    .network(network)
                    .locale(StrUtil.join("-","中国",actualProvince,actualCity))
                    .device(device)
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);
            shortLinkMapper.incrementStats(gid, fullShortUrl, 1, uvFistFlag.get() ? 1 : 0,uipFristFlag ? 1:0);
            LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .date(new Date())
                    .todayPv(1)
                    .todayUv(uvFistFlag.get() ? 1 : 0)
                    .todayUip(uipFristFlag ? 1:0)
                    .build();
            linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);


        }catch (Throwable ex){
            log.info("短链接访问量统计异常");
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
