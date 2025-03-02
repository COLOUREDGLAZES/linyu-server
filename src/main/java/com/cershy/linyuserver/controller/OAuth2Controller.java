package com.cershy.linyuserver.controller;

import cn.hutool.json.JSONObject;
import com.cershy.linyuserver.annotation.UrlFree;
import com.cershy.linyuserver.annotation.UserIp;
import com.cershy.linyuserver.config.OAuth2Config.OAuth2Properties;
import com.cershy.linyuserver.service.CacheService;
import com.cershy.linyuserver.service.OAuth2Service;
import com.cershy.linyuserver.utils.ResultUtil;;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/oauth2")
@Slf4j
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;
    private final OAuth2Properties githubOAuth2Properties;
    private final CacheService cacheService;

    @Value("${oauth2.frontend-url}")
    private String frontendUrl;

    @UrlFree
    @GetMapping("/github/authorize")
    public Object githubAuthorize() {
        String redirectUrl = githubOAuth2Properties.getAuthorizeUrl() +
                "?client_id=" + githubOAuth2Properties.getClientId() +
                "&redirect_uri=" + githubOAuth2Properties.getRedirectUri() +
                "&scope=read:user,user:email";

        JSONObject result = new JSONObject();
        result.put("redirectUrl", redirectUrl);
        return ResultUtil.Succeed(result);
    }


    @UrlFree
    @GetMapping("/github/callback")
    public void githubCallback(@RequestParam("code") String code, @UserIp String userIp, HttpServletResponse response) {
        try {
            Map<String, Object> githubUserInfo = oAuth2Service.getGithubAccessTokenAndUserInfo(code, response);
            if (githubUserInfo == null || githubUserInfo.isEmpty()) {
                response.sendRedirect(frontendUrl + "?error=获取GitHub用户信息失败");
                return;
            }

            JSONObject result = oAuth2Service.handleGithubLogin(githubUserInfo, userIp);
            if (result.getInt("code") == 0) {
                String tempToken = UUID.randomUUID().toString();
                cacheService.setWithExpiry("oauth_temp:" + tempToken, result.getJSONObject("data").toString(), Duration.ofMinutes(15));
                response.sendRedirect(frontendUrl + "?oauthToken=" + tempToken);
            } else {
                response.sendRedirect(frontendUrl + "?error=" + URLEncoder.encode(result.getStr("msg"), "UTF-8"));
            }
        } catch (Exception e) {
            log.error("GitHub 登录处理失败", e);
            try {
                response.sendRedirect(frontendUrl + "?error=登录处理失败");
            } catch (IOException ex) {
                log.error("重定向失败", ex);
            }
        }
    }

    @UrlFree
    @GetMapping("/github/token")
    public JSONObject exchangeOAuthToken(@RequestParam("tempToken") String tempToken) {
        String userDataJson = cacheService.get("oauth_temp:" + tempToken);
        if (userDataJson != null) {
            try {
                JSONObject userData = new JSONObject(userDataJson);
                cacheService.delete("oauth_temp:" + tempToken);
                return ResultUtil.Succeed(userData);
            } catch (Exception e) {
                log.error("解析用户数据失败", e);
                return ResultUtil.Fail("解析用户数据失败");
            }
        }
        return ResultUtil.Fail("无效或已过期的临时授权");
    }
}
