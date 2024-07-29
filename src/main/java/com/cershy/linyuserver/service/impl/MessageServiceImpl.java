package com.cershy.linyuserver.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cershy.linyuserver.constant.MessageContentType;
import com.cershy.linyuserver.entity.ChatList;
import com.cershy.linyuserver.entity.Message;
import com.cershy.linyuserver.entity.MessageRetraction;
import com.cershy.linyuserver.entity.ext.MsgContent;
import com.cershy.linyuserver.exception.LinyuException;
import com.cershy.linyuserver.mapper.MessageMapper;
import com.cershy.linyuserver.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cershy.linyuserver.utils.MinioUtil;
import com.cershy.linyuserver.vo.message.MessageRecordVo;
import com.cershy.linyuserver.vo.message.ReeditMsgVo;
import com.cershy.linyuserver.vo.message.RetractionMsgVo;
import com.cershy.linyuserver.vo.message.SendMsgToUserVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 消息表 服务实现类
 * </p>
 *
 * @author heath
 * @since 2024-05-17
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Resource
    FriendService friendService;

    @Resource
    WebSocketService webSocketService;

    @Resource
    ChatListService chatListService;

    @Resource
    MessageMapper messageMapper;

    @Resource
    MinioUtil minioUtil;

    @Resource
    MessageRetractionService messageRetractionService;

    public Message sendMessage(String userId, String toUserId, MsgContent msgContent) {
        //验证是否是好友
        boolean isFriend = friendService.isFriend(userId, toUserId);
        if (!isFriend) {
            throw new LinyuException("双方非好友");
        }
        //获取上一条显示时间的消息
        Message previousMessage = messageMapper.getPreviousShowTimeMsg(userId, toUserId);
        //存入数据库
        Message message = new Message();
        message.setId(IdUtil.randomUUID());
        message.setFromId(userId);
        message.setToId(toUserId);
        if (null == previousMessage) {
            message.setIsShowTime(true);
        } else {
            message.setIsShowTime(DateUtil.between(new Date(), previousMessage.getUpdateTime(), DateUnit.MINUTE) > 5);
        }
        //设置内容
        msgContent.setFormUserId(userId);
        if (!MessageContentType.Text.equals(msgContent.getType())) {
            JSONObject content = JSONUtil.parseObj(msgContent.getContent());
            String name = (String) content.get("name");
            String type = name.substring(name.lastIndexOf(".") + 1);
            String fileName = userId + "/" + toUserId + "/" + IdUtil.randomUUID() + "." + type;
            content.set("fileName", fileName);
            content.set("url", minioUtil.getUrl(fileName));
            content.set("type", type);
            msgContent.setContent(content.toJSONString(0));
        }
        message.setMsgContent(msgContent);
        boolean isSave = save(message);
        if (isSave) {
            //发送消息
            webSocketService.sendMsgToUser(message, toUserId);
            //更新聊天列表
            chatListService.updateChatList(toUserId, userId, msgContent);
            return message;
        }
        return null;
    }

    @Override
    public Message sendMessageToUser(String userId, SendMsgToUserVo sendMsgToUserVo) {
        return sendMessage(userId, sendMsgToUserVo.getToUserId(), sendMsgToUserVo.getMsgContent());
    }

    @Override
    public List<Message> messageRecord(String userId, MessageRecordVo messageRecordVo) {
        List<Message> messages = messageMapper.messageRecord(userId, messageRecordVo.getTargetId(),
                messageRecordVo.getIndex(), messageRecordVo.getNum());
        return messages;
    }

    @Override
    public Message sendFileMessageToUser(String userId, String toUserId, JSONObject fileInfo) {
        MsgContent msgContent = new MsgContent();
        msgContent.setContent(fileInfo.toJSONString(0));
        msgContent.setType(MessageContentType.File);
        return sendMessage(userId, toUserId, msgContent);
    }

    @Override
    public MsgContent getFileMsgContent(String userId, String msgId) {
        Message msg = getById(msgId);
        if (msg == null) {
            throw new LinyuException("消息为空");
        }
        if (msg.getFromId().equals(userId) || msg.getToId().equals(userId)) {
            return msg.getMsgContent();
        } else {
            throw new LinyuException("消息为空");
        }
    }

    @Override
    public boolean updateMsgContent(String msgId, MsgContent msgContent) {
        LambdaUpdateWrapper<Message> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Message::getMsgContent, msgContent)
                .eq(Message::getId, msgId);
        return update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public Message retractionMsg(String userId, RetractionMsgVo retractionMsgVo) {
        Message message = getById(retractionMsgVo.getMsgId());
        if (null == message)
            throw new LinyuException("消息不存在");
        MsgContent msgContent = message.getMsgContent();
        //设置type为撤销前的类型
        message.setType(msgContent.getType());
        //只有文本才保存，之前的消息内容
        if (MessageContentType.Text.equals(msgContent.getType())) {
            MessageRetraction messageRetraction = new MessageRetraction();
            messageRetraction.setMsgId(IdUtil.randomUUID());
            messageRetraction.setMsgId(message.getId());
            messageRetraction.setMsgContent(msgContent);
            messageRetractionService.save(messageRetraction);

        }

        msgContent.setType(MessageContentType.Retraction);
        msgContent.setContent("");
        updateById(message);

        ChatList userIdchatList = chatListService.getChatListByUserIdAndFromId(userId, message.getToId());
        userIdchatList.setLastMsgContent(msgContent);
        chatListService.updateById(userIdchatList);

        ChatList toIdchatList = chatListService.getChatListByUserIdAndFromId(message.getToId(), userId);
        toIdchatList.setLastMsgContent(msgContent);
        chatListService.updateById(toIdchatList);

        webSocketService.sendMsgToUser(message, message.getToId());
        return message;
    }

    @Override
    public MessageRetraction reeditMsg(String userId, ReeditMsgVo reeditMsgVo) {
        LambdaQueryWrapper<MessageRetraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MessageRetraction::getMsgId, reeditMsgVo.getMsgId());
        return messageRetractionService.getOne(queryWrapper);
    }

    @Override
    public String sendFileOrImg(String userId, String msgId, HttpServletRequest request) throws IOException {
        MsgContent msgContent = getFileMsgContent(userId, msgId);
        JSONObject fileInfo = JSONUtil.parseObj(msgContent.getContent());
        String url = minioUtil.uploadFile(request.getInputStream(), fileInfo.get("fileName").toString(), fileInfo.getLong("size"));
        return url;
    }
}
