package com.eyu.handler;

import com.eyu.entity.bo.ChatBO;
import com.eyu.exception.ChatException;
import com.eyu.service.InteractService;
import com.eyu.util.BotUtil;
import net.mamoe.mirai.contact.MessageTooLargeException;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import net.mamoe.mirai.message.data.QuoteReply;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

/**
 * 事件处理
 *
 * @author zqzq3
 * @date 2023/2/1
 */
@Component
public class MessageEventHandler implements ListenerHost {
    @Resource
    private InteractService interactService;

    private static final String RESET_WORD = "重置会话";

    /**
     * 监听消息并把ChatGPT的回答发送到对应qq/群
     * 注：如果是在群聊则需@
     *
     * @param event 事件 ps:此处是MessageEvent 故所有的消息事件都会被监听
     */
    @EventHandler
    public void onMessage(@NotNull MessageEvent event){
        boolean flag = decide(event.getMessage().contentToString());
        if(flag){
            return;
        }
        ChatBO chatBO = new ChatBO();
        chatBO.setSessionId(String.valueOf(event.getSubject().getId()));
        if (event.getBot().getGroups().contains(event.getSubject().getId())) {
            //如果是在群聊
            if (event.getMessage().contains(new At(event.getBot().getId()))) {
                //存在@机器人的消息就向ChatGPT提问
                //去除@再提问
                String prompt = event.getMessage().contentToString().replace("@" + event.getBot().getId(), "").trim();
                response(event, chatBO, prompt);
            }
        } else {
            //不是在群聊 则直接回复
            String prompt = event.getMessage().contentToString().trim();
            response(event, chatBO, prompt);
        }
    }

    private boolean decide(String str) {
        //此处可以加屏蔽字 来屏蔽一些不想回复的信息
        return false;
    }

    private void response(@NotNull MessageEvent event, ChatBO chatBO, String prompt) {
        if (RESET_WORD.equals(prompt)) {
            //检测到重置会话指令
            BotUtil.resetPrompt(chatBO.getSessionId());
            event.getSubject().sendMessage("重置会话成功");
        } else {
            String response;
            try {
                chatBO.setPrompt(prompt);
                response = interactService.chat(chatBO);
            }catch (ChatException e){
                response = e.getMessage();
            }
            try {
                MessageChain messages = new MessageChainBuilder()
                        .append(new QuoteReply(event.getMessage()))
                        .append(response)
                        .build();
                event.getSubject().sendMessage(messages);
            }catch (MessageTooLargeException e){
                //信息太大，无法引用，采用直接回复
                event.getSubject().sendMessage(response);
            }
        }
    }
}