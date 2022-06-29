package com.linebot.handler;


import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.DatetimePickerAction;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.group.GroupMemberCountResponse;
import com.linecorp.bot.model.group.GroupSummaryResponse;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.sender.Sender;
import com.linecorp.bot.model.message.template.*;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.model.room.RoomMemberCountResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.singletonList;

@LineMessageHandler
@Slf4j
public class TextMessageHandler {

    private final LineMessagingClient lineMessagingClient;

    public TextMessageHandler(LineMessagingClient lineMessagingClient) {
        this.lineMessagingClient = lineMessagingClient;

    }

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        log.info("event: {}", event);
        TextMessageContent message = event.getMessage();
        handleTextContent(event.getReplyToken(), event, message);
        lineMessagingClient.getMessageEvent("text");
    }

    private void reply(@NonNull String replyToken,
                       @NonNull List<Message> messages,
                       boolean notificationDisabled) {
        try {
            BotApiResponse apiResponse = lineMessagingClient
                    .replyMessage(new ReplyMessage(replyToken, messages, notificationDisabled))
                    .get();
            log.info("Sent messages: {}", apiResponse);

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) {
        reply(replyToken, singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
        reply(replyToken, messages, false);
    }

    private void replyText(@NonNull String replyToken, @NonNull String message) {
        if (replyToken.isEmpty()) {
            throw new IllegalArgumentException("replyToken must not be empty");
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000 - 2) + "……";
        }
        this.reply(replyToken, new TextMessage(message));
    }

    private static URI createUri(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .scheme("https")
                .path(path).build()
                .toUri();
    }


    private void handleTextContent(String replyToken, Event event, TextMessageContent content)
            throws Exception {
        final String text = content.getText();

        log.info("Got text message from replyToken:{}: text:{} emojis:{}", replyToken, text,
                content.getEmojis());
        switch (text) {
            case "profile": {
                log.info("Invoking 'profile' command: source:{}",
                        event.getSource());
                final String userId = event.getSource().getUserId();
                if (userId != null) {
                    if (event.getSource() instanceof GroupSource) {
                        lineMessagingClient
                                .getGroupMemberProfile(((GroupSource) event.getSource()).getGroupId(), userId)
                                .whenComplete((profile, throwable) -> {
                                    if (throwable != null) {
                                        this.replyText(replyToken, throwable.getMessage());
                                        return;
                                    }
                                    this.reply(replyToken, Arrays.asList(new TextMessage("(from group)"),
                                            new TextMessage("Display name: " + profile.getDisplayName()),
                                            new ImageMessage(profile.getPictureUrl(), profile.getPictureUrl()))
                                    );
                                });
                    } else {
                        lineMessagingClient
                                .getProfile(userId)
                                .whenComplete((profile, throwable) -> {
                                    if (throwable != null) {
                                        this.replyText(replyToken, throwable.getMessage());
                                        return;
                                    }

                                    this.reply(
                                            replyToken,
                                            Arrays.asList(new TextMessage("Display name: " + profile.getDisplayName()),
                                                    new TextMessage("Status message: " + profile.getStatusMessage()))
                                    );
                                });
                    }
                } else {
                    this.replyText(replyToken, "Bot can't use profile API without user ID");
                }
                break;
            }
            case "bye": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    this.replyText(replyToken, "Leaving group");
                    lineMessagingClient.leaveGroup(((GroupSource) source).getGroupId()).get();
                } else if (source instanceof RoomSource) {
                    this.replyText(replyToken, "Leaving room");
                    lineMessagingClient.leaveRoom(((RoomSource) source).getRoomId()).get();
                } else {
                    this.replyText(replyToken, "Bot can't leave from 1:1 chat");
                }
                break;
            }
            case "group_summary": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    GroupSummaryResponse groupSummary = lineMessagingClient.getGroupSummary(
                            ((GroupSource) source).getGroupId()).get();
                    this.replyText(replyToken, "Group summary: " + groupSummary);
                } else {
                    this.replyText(replyToken, "You can't use 'group_summary' command for "
                            + source);
                }
                break;
            }
            case "group_member_count": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    GroupMemberCountResponse groupMemberCountResponse = lineMessagingClient.getGroupMemberCount(
                            ((GroupSource) source).getGroupId()).get();
                    this.replyText(replyToken, "Group member count: "
                            + groupMemberCountResponse.getCount());
                } else {
                    this.replyText(replyToken, "You can't use 'group_member_count' command  for "
                            + source);
                }
                break;
            }
            case "room_member_count": {
                Source source = event.getSource();
                if (source instanceof RoomSource) {
                    RoomMemberCountResponse roomMemberCountResponse = lineMessagingClient.getRoomMemberCount(
                            ((RoomSource) source).getRoomId()).get();
                    this.replyText(replyToken, "Room member count: "
                            + roomMemberCountResponse.getCount());
                } else {
                    this.replyText(replyToken, "You can't use 'room_member_count' command  for "
                            + source);
                }
                break;
            }
            case "confirm": {
                ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                        "Do it?",
                        new MessageAction("Yes", "Yes!"),
                        new MessageAction("No", "No!")
                );
                TemplateMessage templateMessage = new TemplateMessage("Confirm alt text", confirmTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "carousel": {
                //URI imageUrl = createUri("/static/doge.jpeg");
                URI uri = new URI("https://i.imgur.com/XZuc9d2.jpg");
                CarouselTemplate carouselTemplate = new CarouselTemplate(
                        Arrays.asList(
                                new CarouselColumn(uri, "hoge", "fuga", Arrays.asList(
                                        new URIAction("Go to line.me",
                                                URI.create("https://line.me"), null),
                                        new URIAction("Go to line.me",
                                                URI.create("https://line.me"), null),
                                        new PostbackAction("Say hello1",
                                                "hello こんにちは")
                                )),
                                new CarouselColumn(uri, "hoge", "fuga", Arrays.asList(
                                        new PostbackAction("言 hello2",
                                                "hello こんにちは",
                                                "hello こんにちは"),
                                        new PostbackAction("言 hello2",
                                                "hello こんにちは",
                                                "hello こんにちは"),
                                        new MessageAction("Say message",
                                                "Rice=米")
                                )),
                                new CarouselColumn(uri, "Datetime Picker",
                                        "Please select a date, time or datetime", Arrays.asList(
                                        DatetimePickerAction.OfLocalDatetime
                                                .builder()
                                                .label("Datetime")
                                                .data("action=sel")
                                                .initial(LocalDateTime.parse("2017-06-18T06:15"))
                                                .min(LocalDateTime.parse("1900-01-01T00:00"))
                                                .max(LocalDateTime.parse("2100-12-31T23:59"))
                                                .build(),
                                        DatetimePickerAction.OfLocalDate
                                                .builder()
                                                .label("Date")
                                                .data("action=sel&only=date")
                                                .initial(LocalDate.parse("2017-06-18"))
                                                .min(LocalDate.parse("1900-01-01"))
                                                .max(LocalDate.parse("2100-12-31"))
                                                .build(),
                                        DatetimePickerAction.OfLocalTime
                                                .builder()
                                                .label("Time")
                                                .data("action=sel&only=time")
                                                .initial(LocalTime.parse("06:15"))
                                                .min(LocalTime.parse("00:00"))
                                                .max(LocalTime.parse("23:59"))
                                                .build()
                                ))
                        ));
                TemplateMessage templateMessage = new TemplateMessage("Carousel alt text", carouselTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "image_carousel": {
                URI imageUrl = createUri("/static/doge.jpeg");
                ImageCarouselTemplate imageCarouselTemplate = new ImageCarouselTemplate(
                        Arrays.asList(
                                new ImageCarouselColumn(imageUrl,
                                        new URIAction("Goto line.me",
                                                URI.create("https://line.me"), null)
                                ),
                                new ImageCarouselColumn(imageUrl,
                                        new MessageAction("Say message",
                                                "Rice=米")
                                ),
                                new ImageCarouselColumn(imageUrl,
                                        new PostbackAction("言 hello2",
                                                "hello こんにちは",
                                                "hello こんにちは")
                                )
                        ));
                TemplateMessage templateMessage = new TemplateMessage("ImageCarousel alt text",
                        imageCarouselTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "flex":
                //this.reply(replyToken, new ExampleFlexMessageSupplier().get());
                break;
            case "quickreply":
                //this.reply(replyToken, new MessageWithQuickReplySupplier().get());
                break;
            case "icon":
                this.reply(replyToken,
                        TextMessage.builder()
                                .text("Hello, I'm cat! Meow~")
                                .sender(Sender.builder()
                                        .name("Cat")
                                        .iconUrl(new URI("https://i.imgur.com/XZuc9d2.jpg"))
                                        .build())
                                .build());
                break;
            default:
                log.info("Returns echo message {}: {}", replyToken, text);
                this.replyText(replyToken, text);
                break;
        }
    }}



