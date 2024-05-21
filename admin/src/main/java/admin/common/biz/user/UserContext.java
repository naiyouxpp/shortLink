package admin.common.biz.user;


import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Optional;

public class UserContext {
     /*
   ThreadLocal不支持跨线程，而阿里巴巴这个TransmittableThreadLocal支持跨线程
   private static final ThreadLocal<UserInfoDTO> USER_THREAD_LOCAL = new ThreadLocal<>();
   */
    private static final ThreadLocal<UserInfoDTO> USER_THREAD_LOCAL = new TransmittableThreadLocal<>();


    public static void setCurrentUser(UserInfoDTO user) {
        USER_THREAD_LOCAL.set(user);
    }

    //使用username值
    public static String getCurrentUser() {
        UserInfoDTO userInfoDTO = USER_THREAD_LOCAL.get();
        return Optional.ofNullable(userInfoDTO).map(UserInfoDTO::getUsername).orElse(null);
    }
    public static void removeCurrentUser() {
        USER_THREAD_LOCAL.remove();
    }
}
