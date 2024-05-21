package admin.service;

import admin.dao.entity.UserDO;
import admin.dto.req.UserLoginReqDTO;
import admin.dto.req.UserRegisterReqDTO;
import admin.dto.req.UserUpdateReqDTO;
import admin.dto.resp.UserLoginRespDTO;
import admin.dto.resp.UserRespDTO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserService extends IService<UserDO> {

    UserRespDTO getUserByUsername(String username);

    Boolean hasUsername(String username);

    void userRegister(UserRegisterReqDTO requestParam);

    void userUpdate(UserUpdateReqDTO userUpdateReqDTO);


    UserLoginRespDTO Login(UserLoginReqDTO requestParam);

    Boolean hasLogin(String username,String token);

    Boolean logout(String username,String token);

}
