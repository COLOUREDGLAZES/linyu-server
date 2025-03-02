package com.cershy.linyuserver.dto;

import lombok.Data;

import java.util.Date;

@Data
public class UserDto {
    private String id;
    private String thirdPartyId;  // 用于存储第三方平台的用户ID，格式如：github_12345678
    private String thirdPartyType;  // 用于存储第三方平台的用户ID，格式如：github_12345678
    private String account;
    private String name;
    private String portrait;
    private String sex;
    private Date birthday;
    private String signature;
    private String phone;
    private String email;
}
