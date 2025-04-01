package com.example.webrtc;

import lombok.Getter;
import org.kurento.client.*;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/*
개별 회의방의 미디어 스트림 관리
참가자 간 연결 관리

joinRoom(): 새로운 참가자 추가 및 연결
connectParticipants(): 참가자 간 미디어 스트림 연결
leaveRoom(): 참가자 제거

pipeline: Kurento 미디어 파이프라인
participants: 참가자 ID와 WebRtcEndpoint 매핑
*/

@Getter
public class Room implements Closeable {
    private final String roomId;
    private final MediaPipeline pipeline;
    private final ConcurrentHashMap<String, WebRtcEndpoint> participants = new ConcurrentHashMap<>();

    public Room(String roomId, KurentoClient kurentoClient) {
        this.roomId = roomId;
        this.pipeline = kurentoClient.createMediaPipeline();
    }

    public WebRtcEndpoint joinRoom(String participantId, WebSocketSession session) {
        WebRtcEndpoint endpoint = new WebRtcEndpoint.Builder(pipeline).build();
        
        participants.forEach((id, existingEndpoint) -> {
            if (!id.equals(participantId)) {
                connectParticipants(endpoint, existingEndpoint);
                connectParticipants(existingEndpoint, endpoint);
            }
        });

        participants.put(participantId, endpoint);
        return endpoint;
    }

    private void connectParticipants(WebRtcEndpoint from, WebRtcEndpoint to) {
        from.connect(to);
    }

    public void leaveRoom(String participantId) {
        WebRtcEndpoint endpoint = participants.remove(participantId);
        if (endpoint != null) {
            endpoint.release();
        }
    }

    @Override
    public void close() {
        participants.values().forEach(WebRtcEndpoint::release);
        pipeline.release();
    }
} 