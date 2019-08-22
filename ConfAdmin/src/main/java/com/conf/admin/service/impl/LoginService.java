package com.conf.admin.service.impl;

import com.conf.admin.core.model.ConfUser;
import com.conf.admin.dao.ConfUserDao;
import com.conf.admin.core.util.CookieUtil;
import com.conf.admin.core.util.JacksonUtil;
import com.conf.admin.core.util.ReturnT;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;

/**
 * Login Service
 */
@Configuration
public class LoginService {

    public static final String LOGIN_IDENTITY = "XXL_CONF_LOGIN_IDENTITY";

    @Resource
    private ConfUserDao confUserDao;

    private String makeToken(ConfUser confUser){
        String tokenJson = JacksonUtil.writeValueAsString(confUser);
        String tokenHex = new BigInteger(tokenJson.getBytes()).toString(16);
        return tokenHex;
    }
    private ConfUser parseToken(String tokenHex){
        ConfUser confUser = null;
        if (tokenHex != null) {
            String tokenJson = new String(new BigInteger(tokenHex, 16).toByteArray());      // username_password(md5)
            confUser = JacksonUtil.readValue(tokenJson, ConfUser.class);
        }
        return confUser;
    }

    /**
     * login
     *
     * @param response
     * @param usernameParam
     * @param passwordParam
     * @param ifRemember
     * @return
     */
    public ReturnT<String> login(HttpServletResponse response, String usernameParam, String passwordParam, boolean ifRemember){

        ConfUser confUser = confUserDao.load(usernameParam);
        if (confUser == null) {
            return new ReturnT<String>(500, "账号或密码错误");
        }

        String passwordParamMd5 = DigestUtils.md5DigestAsHex(passwordParam.getBytes());
        if (!confUser.getPassword().equals(passwordParamMd5)) {
            return new ReturnT<String>(500, "账号或密码错误");
        }

        String loginToken = makeToken(confUser);

        // do login
        CookieUtil.set(response, LOGIN_IDENTITY, loginToken, ifRemember);
        return ReturnT.SUCCESS;
    }

    /**
     * logout
     *
     * @param request
     * @param response
     */
    public void logout(HttpServletRequest request, HttpServletResponse response){
        CookieUtil.remove(request, response, LOGIN_IDENTITY);
    }

    /**
     * logout
     *
     * @param request
     * @return
     */
    public ConfUser ifLogin(HttpServletRequest request){
        String cookieToken = CookieUtil.getValue(request, LOGIN_IDENTITY);
        if (cookieToken != null) {
            ConfUser cookieUser = parseToken(cookieToken);
            if (cookieUser != null) {
                ConfUser dbUser = confUserDao.load(cookieUser.getUsername());
                if (dbUser != null) {
                    if (cookieUser.getPassword().equals(dbUser.getPassword())) {
                        return dbUser;
                    }
                }
            }
        }
        return null;
    }

}
