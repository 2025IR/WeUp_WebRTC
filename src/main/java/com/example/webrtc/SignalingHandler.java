package com.example.webrtc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/*
WebSocket을 통한 클라이언트-서버 통신 처리
WebRTC 시그널링 중계

handleJoinRoom(): 새로운 참가자 처리
handleOffer(): WebRTC offer 처리
handleIceCandidate(): ICE candidate 처리
handleLeaveRoom(): 참가자 퇴장 처리

sessionToRoom: WebSocket 세션과 룸 ID 매핑
sessionToEndpoint: WebSocket 세션과 Kurento 엔드포인트 매핑
*/


//웹소켓 통한 시그널링 처리 -> 클라 - 쿠렌토 사이에서 통신
@RequiredArgsConstructor
public class SignalingHandler extends TextWebSocketHandler {
    private static final Gson gson = new Gson();
    private final RoomManager roomManager;
    private final ConcurrentHashMap<String, String> sessionToRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebRtcEndpoint> sessionToEndpoint = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        String type = jsonMessage.get("type").getAsString();

        switch (type) {
            case "joinRoom":
                handleJoinRoom(session, jsonMessage);
                break;
            case "offer":
                handleOffer(session, jsonMessage);
                break;
            case "iceCandidate":
                handleIceCandidate(session, jsonMessage);
                break;
            case "leaveRoom":
                handleLeaveRoom(session);
                break;
        }
    }

    private void handleJoinRoom(WebSocketSession session, JsonObject jsonMessage) throws IOException {
        String roomId = jsonMessage.get("roomId").getAsString();
        Room room = roomManager.getRoom(roomId); //가져와서

        //쿠렌토 엔드포인트 만들고
        WebRtcEndpoint endpoint = room.joinRoom(session.getId(), session);
        sessionToRoom.put(session.getId(), roomId);
        sessionToEndpoint.put(session.getId(), endpoint);

        // ICE 그거 리스너설정하고 처리하고
        endpoint.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("type", "iceCandidate");
            response.add("candidate", gson.toJsonTree(event.getCandidate()));
            try {
                session.sendMessage(new TextMessage(response.toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // 그거 성공했으면 메세지보내고
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomJoined");
        session.sendMessage(new TextMessage(response.toString()));
    }

    private void handleOffer(WebSocketSession session, JsonObject jsonMessage) throws IOException {
        WebRtcEndpoint endpoint = sessionToEndpoint.get(session.getId());
        String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        String sdpAnswer = endpoint.processOffer(sdpOffer);

        JsonObject response = new JsonObject();
        response.addProperty("type", "answer");
        response.addProperty("sdpAnswer", sdpAnswer);
        session.sendMessage(new TextMessage(response.toString()));

        endpoint.gatherCandidates();
    }

    private void handleIceCandidate(WebSocketSession session, JsonObject jsonMessage) {
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
        WebRtcEndpoint endpoint = sessionToEndpoint.get(session.getId());
        
        IceCandidate iceCandidate = new IceCandidate(
            candidate.get("candidate").getAsString(),
            candidate.get("sdpMid").getAsString(),
            candidate.get("sdpMLineIndex").getAsInt());
        
        endpoint.addIceCandidate(iceCandidate);
    }

    private void handleLeaveRoom(WebSocketSession session) {
        String roomId = sessionToRoom.remove(session.getId());
        if (roomId != null) {
            Room room = roomManager.getRoom(roomId);
            room.leaveRoom(session.getId());
            sessionToEndpoint.remove(session.getId());
            
            if (room.getParticipants().isEmpty()) {
                roomManager.removeRoom(roomId);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        handleLeaveRoom(session);
    }
} 