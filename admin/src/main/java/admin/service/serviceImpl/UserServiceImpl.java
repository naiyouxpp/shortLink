package admin.service.serviceImpl;

import admin.common.convention.exception.ClientException;
import admin.common.convention.result.Results;
import admin.common.enums.UserErrorCodeEnum;
import admin.dao.entity.UserDO;
import admin.dao.mapper.UserMapper;
import admin.dto.req.UserLoginReqDTO;
import admin.dto.req.UserRegisterReqDTO;
import admin.dto.req.UserUpdateReqDTO;
import admin.dto.resp.UserLoginRespDTO;
import admin.dto.resp.UserRespDTO;
import admin.service.GroupService;
import admin.service.UserService;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static admin.common.enums.UserErrorCodeEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    //为啥不加final关键字就会报错啊
    private final UserMapper userMapper;

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;

    private final GroupService groupService;

    @Override
    public UserRespDTO getUserByUsername(String username) {

        LambdaQueryWrapper<UserDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        lambdaQueryWrapper.eq(UserDO::getUsername, username);

        UserDO userDO = userMapper.selectOne(lambdaQueryWrapper);

        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }

        UserRespDTO userRespDTO = new UserRespDTO();

        BeanUtils.copyProperties(userDO, userRespDTO);

        return userRespDTO;
    }

    @Override
    public Boolean hasUsername(String username) {
        return userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void userRegister(UserRegisterReqDTO requestParam) {
        //用户名已存在
        if (hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        //使用锁，解决大量请求同个username的问题，使用redissonClient获取锁
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            //锁是不能重名的，重名的锁其实都是同一个锁，如果该名称的锁已经在使用则无法再次lock，所以进行lock判断是否已经有重名的锁正在使用
            if (lock.tryLock()) {
                //正常注册
                int insert = userMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
                if (insert < 1) {
                    throw new ClientException(USER_SAVE_ERROR);
                }
                //没问题则将username存进布隆过滤器
                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
                groupService.saveGroup(requestParam.getUsername(),"默认分组");
                return ;
            }
            //trylock失败表示该锁已存在并且正在使用，抛异常
            throw new ClientException(USER_NAME_EXIST);
        } finally {
            //释放锁
            lock.unlock();
        }

    }

    @Override
    public void userUpdate(UserUpdateReqDTO userUpdateReqDTO) {
        LambdaQueryWrapper<UserDO> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(UserDO::getUsername,userUpdateReqDTO.getUsername());
        UserDO userDO = BeanUtil.toBean(userUpdateReqDTO, UserDO.class);
        userMapper.update(userDO,lambdaQueryWrapper);
    }

    @SneakyThrows
    @Override
    public UserLoginRespDTO Login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> lambdaQueryWrapper  = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(UserDO::getUsername,requestParam.getUsername());
        lambdaQueryWrapper.eq(UserDO::getPassword,requestParam.getPassword());
        lambdaQueryWrapper.eq(UserDO::getDelFlag,0);
        UserDO userDO = userMapper.selectOne(lambdaQueryWrapper);

        //重心不在登录上，一般应该先判断用户名是否存在，若不存在则返回“用户名不存在”，如果用户名存在而密码不匹配则返回“密码错误”，且这里没有验证码
        if (userDO == null){
            throw new ClientException("用户不存在");
        }

        //检查用户是否已经登录
        //那岂不是不能登录两次了？假如不小心退出咋办...

        Boolean hasLogin = stringRedisTemplate.hasKey("login_"+requestParam.getUsername());
        if (hasLogin!= null&&hasLogin){
            throw new ClientException("用户已登录");
        }

        String uuid = String.valueOf(UUID.randomUUID());
        ObjectMapper objectMapper = new ObjectMapper();
        String userJson = objectMapper.writeValueAsString(userDO);

        stringRedisTemplate.opsForHash().put("login_"+requestParam.getUsername(),uuid, userJson );
        //用户登录不超过30分钟
        stringRedisTemplate.expire("login_"+requestParam.getUsername(),30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean hasLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().hasKey("login_" + username, token);
    }

    @Override
    public Boolean logout(String username, String token) {
        Boolean hasLogin = hasLogin(username, token);
        if (hasLogin){
            stringRedisTemplate.opsForHash().delete("login_"+username, token);
            return true;
        }
        throw new ClientException("用户未登录或用户token错误");
    }

}
