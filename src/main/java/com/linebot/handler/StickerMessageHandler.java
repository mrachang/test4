package com.linebot.handler;

import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.extern.slf4j.Slf4j;

@LineMessageHandler
@Slf4j
public class StickerMessageHandler {

    @EventMapping
    public StickerMessage handleTextMessageEvent(MessageEvent<StickerMessageContent> event) {
        log.info("event: {}", event);
        // sally
        return new StickerMessage("11537", "52002736");
    }
}
