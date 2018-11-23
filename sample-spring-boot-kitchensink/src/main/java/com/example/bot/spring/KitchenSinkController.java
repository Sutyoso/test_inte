/*
* Copyright 2016 LINE Corporation
*
* LINE Corporation licenses this file to you under the Apache License,
* version 2.0 (the "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at:
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/

package com.example.bot.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.LinkedList;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.awt.Desktop;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.Reader;
import java.nio.charset.Charset;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.UnknownServiceException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.linecorp.bot.model.action.DatetimePickerAction;
import com.linecorp.bot.model.message.template.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.io.ByteStreams;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.JoinEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.UnfollowEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.message.VideoMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.AudioMessage;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.ImagemapMessage;
import com.linecorp.bot.model.message.LocationMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.VideoMessage;
import com.linecorp.bot.model.message.imagemap.ImagemapArea;
import com.linecorp.bot.model.message.imagemap.ImagemapBaseSize;
import com.linecorp.bot.model.message.imagemap.MessageImagemapAction;
import com.linecorp.bot.model.message.imagemap.URIImagemapAction;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@LineMessageHandler
public class KitchenSinkController {
    @Autowired
    private LineMessagingClient lineMessagingClient;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        TextMessageContent message = event.getMessage();
        handleTextContent(event.getReplyToken(), event, message);
    }

    @EventMapping
    public void handleUnfollowEvent(UnfollowEvent event) {
        log.info("unfollowed this bot: {}", event);
    }

    @EventMapping
    public void handleFollowEvent(FollowEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Got followed event");
    }

    @EventMapping
    public void handleJoinEvent(JoinEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Joined " + event.getSource());
    }

    @EventMapping
    public void handlePostbackEvent(PostbackEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Got postback data " + event.getPostbackContent().getData() + ", param " + event.getPostbackContent().getParams().toString());
    }

    @EventMapping
    public void handleBeaconEvent(BeaconEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Got beacon message " + event.getBeacon().getHwid());
    }

    @EventMapping
    public void handleOtherEvent(Event event) {
        log.info("Received message(Ignored): {}", event);
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) {
        reply(replyToken, Collections.singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
        try {
            BotApiResponse apiResponse = lineMessagingClient
            .replyMessage(new ReplyMessage(replyToken, messages))
            .get();
            log.info("Sent messages: {}", apiResponse);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
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

    private void handleHeavyContent(String replyToken, String messageId,
    Consumer<MessageContentResponse> messageConsumer) {
        final MessageContentResponse response;
        try {
            response = lineMessagingClient.getMessageContent(messageId)
            .get();
        } catch (InterruptedException | ExecutionException e) {
            reply(replyToken, new TextMessage("Cannot get image: " + e.getMessage()));
            throw new RuntimeException(e);
        }
        messageConsumer.accept(response);
    }

    private void handleSticker(String replyToken, StickerMessageContent content) {
        reply(replyToken, new StickerMessage(
        content.getPackageId(), content.getStickerId())
        );
    }

    HashMap<String,String> storedText = new HashMap<String,String>();
    boolean bossStat = false;

    private void handleTextContent(String replyToken, Event event, TextMessageContent content)
    throws Exception {

        String text = content.getText();
        String[] tArr = text.split(" ");
        String t = tArr[0].toLowerCase();
        log.info("Got text message from {}: {}", replyToken, text);

        switch (t) {
            case "boss": {
                bossStat = true;

                String message = "
                Raja Ampat - Pemandu wisata, anggota Himpunan Pramuwisata Indonesia Kabupaten Raja Ampat, Provinsi Papua Barat, menemukan 11 bangkai hiu terapung di atas sebuah rakit di perairan Kepulauan Pam Waigeo Barat. Bangkai hiu yang sudah membusuk dan tidak ada lagi kulit dan siripnya itu ditemukan oleh seorang pramuwisata bernama Mecu Saleo. Saya melakukan aktivitas di perairan Kepulauan Pam dan beritahu kepada teman-teman (guide) yang lainnya agar disampaikan kepada pihak terkait, kata Mecu, di Waisai, sebagaimana dikutip dari Antara, Jumat (23/11/2018). Mecu Saleo mengatakan penemuan bangkai hiu tersebut pada Kamis 22 November sore saat melintas dengan kapal kecil di perairan Kepulauan Pam, tepatnya di Tanjung Piaynemo. Ia melihat ada dua rakit kecil terapung tanpa ada orang. Dia mengatakan, penasaran dengan rakit tanpa tuan tersebut dia langsung menghampiri dan kaget melihat 11 bangkai hiu yang sudah membusuk. Saya langsung foto bangkai hiu tersebut untuk dilaporkan kepada pihak terkait serta viralkan. Setelah foto bangkai tersebut langsung dibuang karena sudah membusuk dan rakitnya dibawa ke daratan, ujarnya. Menurut dia, bangkai hiu tersebut hanya tinggal daging kulit dan siripnya sudah diambil. Bangkai tersebut sudah mengeluarkan bau tak sedap yang diperkirakan ditangkap tiga hari yang lalu. Kami berharap ada pengawasan yang ketat oleh pemerintah di kawasan perairan Kepulauan Pam karena banyak anak hiu yang disenangi wisatawan saat berkunjung ke Raja Ampat, tambah dia.";
               
                this.replyText(
                replyToken,
                message
                );

                break;
            }
            case "noboss": {
                bossStat = false;
                this.replyText(replyToken,"OK.");
                break;
            }
            case "save": {
                if(!bossStat){
                    if(tArr.length<3){
                        this.replyText(replyToken,"Data yang diberikan kurang lengkap.");
                    }
                    else{
                        String inputText = "";
                        for(int i = 2;i<tArr.length;i++){
                            inputText += tArr[i]+" ";
                        }
                        storedText.put(tArr[1],inputText);
                        this.replyText(replyToken,"OK.");
                    }
                }
                break;
            }
            case "load": {
                if(!bossStat){
                    String r = "";
                    if(storedText.containsKey(tArr[1])){
                        r = storedText.get(tArr[1])+"";
                    }
                    else{
                        r = "Kata Kunci Pencarian Tidak Ditemukan.";
                    }
                    this.replyText(replyToken,r);
                }
                break;
            }
            case "profile": {
                String userId = event.getSource().getUserId();
                if (userId != null) {
                    lineMessagingClient
                    .getProfile(userId)
                    .whenComplete((profile, throwable) -> {
                        if (throwable != null) {
                            this.replyText(replyToken, throwable.getMessage());
                            return;
                        }

                        this.reply(
                        replyToken,
                        Arrays.asList(new TextMessage(
                        "Display name: " + profile.getDisplayName()),
                        new TextMessage("Status message: "
                        + profile.getStatusMessage()))
                        );

                    });
                } else {
                    this.replyText(replyToken, "User ID tidak tersedia.");
                }
                break;
            }
            case "bye": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    this.replyText(replyToken, "Byeee.");
                    lineMessagingClient.leaveGroup(((GroupSource) source).getGroupId()).get();
                } else if (source instanceof RoomSource) {
                    this.replyText(replyToken, "Byeee.");
                    lineMessagingClient.leaveRoom(((RoomSource) source).getRoomId()).get();
                } else {
                    this.replyText(replyToken, "Tidak dapat meninggalkan chat 1:1.");
                }
                break;
            }
            case "calc": {
                if (!bossStat) {
                    double result = 0;
                    if (tArr.length < 4) {
                        this.replyText(replyToken, "calc [operand1][spasi][operator][spasi][operand2]");
                    } else {
                        try{
                            Double.parseDouble(tArr[1]);
                            Double.parseDouble(tArr[3]);
                            if (tArr[2].equals("+")) {
                                result = Double.parseDouble(tArr[1]) + Double.parseDouble(tArr[3]);
                                this.replyText(replyToken, result + "");
                            } else if (tArr[2].equals("-")) {
                                result = Double.parseDouble(tArr[1]) - Double.parseDouble(tArr[3]);
                                this.replyText(replyToken, result + "");
                            } else if (tArr[2].equals("*")) {
                                result = Double.parseDouble(tArr[1]) * Double.parseDouble(tArr[3]);
                                this.replyText(replyToken, result + "");
                            } else if (tArr[2].equals("/")) {
                                if(tArr[3].equals("0")){
                                    this.replyText(replyToken, "Penyebut tidak boleh 0.");
                                }
                                else{
                                    result = Double.parseDouble(tArr[1]) / Double.parseDouble(tArr[3]);
                                    this.replyText(replyToken, result + "");
                                }
                            } else {
                                this.replyText(replyToken, "Operator salah.");
                            }
                        } catch(NumberFormatException e){
                            this.replyText(replyToken, "Operand bukan merupakan bilangan.");
                        }
                    }
                }
                break;
            }
            case "help":{
                if (!bossStat) {
                    this.replyText(replyToken, "1. Boss - Command ini berfungsi untuk menampilkan teks berita secara random dan mematikan fungsi command lain." + "\n" +
                    "2. Noboss - Command ini adalah kebalikan dari command boss, yaitu untuk mengaktifkan kembali fungsi semua command yang ada." + "\n" +
                    "3. Save - Command ini terdiri atas 2 parameter yaitu key dan value untuk menyimpan data yang diinginkan." + "\n" +
                    "4. Load - Command ini terdiri atas 1 parameter yaitu key yang akan mengembalikan value sesuai dengan key yang dimasukan." + "\n" +
                    "5. Profile - Command ini berfungsi untuk menampilkan nama dan status pengguna." + "\n" +
                    "6. Calc - Command ini terdiri atas 3 parameter yaitu [operand1], [operator], dan [operand2] yang berfungsi sebagai kalkulator." + "\n" +
                    "7. Keys - Command untuk memeriksa semua kata kunci yang disimpan." + "\n" +
                    "8. Status - Command untuk memeriksa mode yang sedang digunakan." + "\n" +
                    "9. Bye - Command untuk mengeluarkan bot dari chat group.");
                }
                break;
            }
            case "status": {
                if(!bossStat){
                    this.replyText(replyToken,"Saat ini sedang berada di status Noboss.");
                }else{
                    this.replyText(replyToken,"Saat ini sedang berada di status Boss.");
                }
                break;
            }
            case "keys": {
                if(!bossStat){
                    if (storedText.size() > 0) {
                        this.replyText(replyToken, String.join(", ", storedText.keySet()));
                    }else {
                        this.replyText(replyToken, "Tidak ada data yang disimpan.");
                    }
                }
                break;
            }
            default: {
                if(!bossStat){
                    this.replyText(replyToken, "Untuk mengetahui semua command yang ada, silahkan ketik help.");
                }
                break;
            }
        }
    }

    private static String createUri(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path(path).build()
        .toUriString();
    }

    private void system(String... args) {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        try {
            Process start = processBuilder.start();
            int i = start.waitFor();
            log.info("result: {} =>  {}", Arrays.toString(args), i);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            log.info("Interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static DownloadedContent saveContent(String ext, MessageContentResponse responseBody) {
        log.info("Got content-type: {}", responseBody);

        DownloadedContent tempFile = createTempFile(ext);
        try (OutputStream outputStream = Files.newOutputStream(tempFile.path)) {
            ByteStreams.copy(responseBody.getStream(), outputStream);
            log.info("Saved {}: {}", ext, tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DownloadedContent createTempFile(String ext) {
        String fileName = LocalDateTime.now().toString() + '-' + UUID.randomUUID().toString() + '.' + ext;
        Path tempFile = KitchenSinkApplication.downloadedContentDir.resolve(fileName);
        tempFile.toFile().deleteOnExit();
        return new DownloadedContent(
        tempFile,
        createUri("/downloaded/" + tempFile.getFileName()));
    }

    @Value
    public static class DownloadedContent {
        Path path;
        String uri;
    }
}
