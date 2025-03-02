package com.cershy.linyuserver.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cershy.linyuserver.config.OAuth2Config;
import com.cershy.linyuserver.constant.UserRole;
import com.cershy.linyuserver.constant.UserStatus;
import com.cershy.linyuserver.entity.User;
import com.cershy.linyuserver.mapper.UserMapper;
import com.cershy.linyuserver.service.OAuth2Service;
import com.cershy.linyuserver.utils.ResultUtil;
import com.cershy.linyuserver.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OAuth2ServiceImpl extends ServiceImpl<UserMapper, User> implements OAuth2Service {

    private final UserServiceImpl userService;
    private final RestTemplate restTemplate;
    private final OAuth2Config.OAuth2Properties githubOAuth2Properties;

    @Override
    public JSONObject handleGithubLogin(Map<String, Object> githubUserInfo, String userIp) {
        try {
            String githubId = githubUserInfo.get("id").toString();
            String login = (String) githubUserInfo.get("login");
            String name = (String) githubUserInfo.get("name");
            name = (name == null || name.isEmpty()) ? login : name;
            String email = (String) githubUserInfo.get("email");
            String avatarUrl = (String) githubUserInfo.get("avatar_url");

            // 查找或创建用户
            User user = findOrCreateGithubUser(githubId, login, name, email, avatarUrl);

            // 创建token并返回用户信息
            JSONObject userinfo = userService.createUserToken(user, userIp);
            user.setOnlineEquipment("GitHub登录");
            boolean isSave = updateById(user);

            return isSave ? ResultUtil.Succeed(userinfo) : ResultUtil.Fail("登录失败");
        } catch (Exception e) {
            log.error("GitHub登录处理失败", e);
            return ResultUtil.Fail("登录失败：" + e.getMessage());
        }
    }

    private User findOrCreateGithubUser(String githubId, String login, String name, String email, String avatarUrl) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getThirdPartyId, "github_" + githubId);
        User user = getOne(queryWrapper);

        if (user == null && email != null && !email.isEmpty()) {
            List<User> users = userService.getUserByEmail(email);
            if (!users.isEmpty()) {
                user = users.get(0);
                user.setThirdPartyId("github_" + githubId);
                user.setThirdPartyType("github");
                updateById(user);
            }
        }

        if (user == null) {
            user = new User();
            user.setId(IdUtil.randomUUID());
            user.setName(name);
            user.setAccount("github_" + login);
            user.setThirdPartyId("github_" + githubId);
            user.setThirdPartyType("github");
            String password = RandomUtil.randomString(16);
            String passwordHash = SecurityUtil.hashPassword(password);
            user.setStatus(UserStatus.Normal);
            user.setPassword(passwordHash);
            user.setBirthday(new Date());
            user.setRole(UserRole.User);
            user.setSex("男");
            if (email != null && !email.isEmpty()) {
                user.setEmail(email);
            }
            user.setPortrait(avatarUrl);
            save(user);
        }

        return user;
    }


    @Override
    public Map<String, Object>  getGithubAccessTokenAndUserInfo(String code, HttpServletResponse response) throws Exception{

        String accessToken = getGithubAccessToken(code);
        if (accessToken == null) {
            response.sendRedirect("获取GitHub授权失败");
        }
        return getGithubUserInfo(accessToken);
    }

    private String getGithubAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("client_id", githubOAuth2Properties.getClientId());
        requestBody.put("client_secret", githubOAuth2Properties.getClientSecret());
        requestBody.put("code", code);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                githubOAuth2Properties.getTokenUrl(),
                request,
                Map.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return (String) response.getBody().get("access_token");
        }

        return null;
    }


    private Map<String, Object> getGithubUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                githubOAuth2Properties.getUserInfoUrl(),
                HttpMethod.GET,
                entity,
                Map.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            if (response.getBody().get("email") == null) {
                ResponseEntity<List> emailsResponse = restTemplate.exchange(
                        githubOAuth2Properties.getUserInfoUrl() + "/emails",
                        HttpMethod.GET,
                        entity,
                        List.class
                );

                if (emailsResponse.getStatusCode() == HttpStatus.OK &&
                        emailsResponse.getBody() != null &&
                        !emailsResponse.getBody().isEmpty()) {

                    for (Object emailObj : emailsResponse.getBody()) {
                        Map<String, Object> emailData = (Map<String, Object>) emailObj;
                        if (Boolean.TRUE.equals(emailData.get("primary"))) {
                            response.getBody().put("email", emailData.get("email"));
                            break;
                        }
                    }
                }
            }

            return response.getBody();
        }

        return null;
    }
}
