package com.cershy.linyuserver.service;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cershy.linyuserver.entity.User;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface OAuth2Service extends IService<User> {

    JSONObject handleGithubLogin(Map<String, Object> githubUserInfo, String userIp);

    Map<String, Object>  getGithubAccessTokenAndUserInfo(String code, HttpServletResponse response) throws Exception;
}
