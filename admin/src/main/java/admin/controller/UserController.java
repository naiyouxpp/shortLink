package admin.controller;

import admin.common.convention.result.Result;
import admin.common.convention.result.Results;
import admin.dto.req.UserLoginReqDTO;
import admin.dto.req.UserRegisterReqDTO;
import admin.dto.req.UserUpdateReqDTO;
import admin.dto.resp.UserLoginRespDTO;
import admin.dto.resp.UserRespDTO;
import admin.service.UserService;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {


    private final UserService userService;


    @GetMapping("/getOneByUsername/{username}")
    public Result<UserRespDTO> getOne(@PathVariable String username) {

        return Results.success(userService.getUserByUsername(username));
    }


    @GetMapping("/has-username")
    public  Result<Boolean> hasUserName(@PathParam("username") String username){
        return Results.success(!userService.hasUsername(username));
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam){
        userService.userRegister(requestParam);
        return Results.success();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam){
        userService.userUpdate(requestParam);
        return Results.success();
    }


    @GetMapping("/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam){
        return Results.success(userService.Login(requestParam));
    }

    @GetMapping("/has-login")
    public Result<Boolean> haslogin(@PathParam("username")String username,@PathParam("token")String token){
        return Results.success(userService.hasLogin(username,token));
    }

    @DeleteMapping("/logout")
    public Result<Void> logout(@PathParam("username")String username,@PathParam("token")String token){
        userService.logout(username,token);
        return Results.success();
    }
}
