package com.example.webrtc;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
/*
애플리케이션의 진입점
필요한 모든 컴포넌트들을 초기화하고 연결
WebSocket 설정
Kurento 서버와의 연결 설정

kurentoClient(): Kurento Media Server와의 연결을 생성
roomManager(): 회의방 관리를 위한 RoomManager 인스턴스 생성
signalingHandler(): WebSocket 통신을 처리하는 핸들러 생성
registerWebSocketHandlers(): WebSocket 엔드포인트 설정
*/


@SpringBootApplication
@EnableWebSocket
public class WebRTCApplication implements WebSocketConfigurer {

    //쿠렌토 서버 연결설정하고 웹소켓 핸들러 등록하고 룸매니저 빈 등록하고 시그널핸들러 빈 등록하고 메인 실행
    @Bean
    public KurentoClient kurentoClient() {
        return KurentoClient.create("ws://localhost:8888/kurento");
        //return KurentoClient.create("ws://host.docker.internal:8888/kurento");

    }

    @Bean
    public RoomManager roomManager() {
        return new RoomManager();
    }

    @Bean
    public SignalingHandler signalingHandler() {
        return new SignalingHandler(roomManager());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler(), "/signal")
                .setAllowedOrigins("*")
                .setAllowedOriginPatterns("*");
    }

    public static void main(String[] args) {
        SpringApplication.run(WebRTCApplication.class, args);
    }
} 