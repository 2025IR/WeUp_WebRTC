package com.example.webrtc;

import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;

/*
전체 회의방 관리
룸 생성 및 검색

getRoom(): 룸 조회 또는 생성
removeRoom(): 룸 제거

*/



public class RoomManager {
    @Autowired
    private KurentoClient kurentoClient;

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            room = new Room(roomId, kurentoClient);
            rooms.put(roomId, room);
        }
        return room;
    }

    public void removeRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null) {
            room.close();
        }
    }
} 