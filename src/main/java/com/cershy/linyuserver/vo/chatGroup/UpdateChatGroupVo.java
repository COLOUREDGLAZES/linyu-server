package com.cershy.linyuserver.vo.chatGroup;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UpdateChatGroupVo {
    @NotNull(message = "群不能为空~")
    private String groupId;
    private String updateKey;
    private String updateValue;
}