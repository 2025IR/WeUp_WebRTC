/*
브라우저에서 WebRTC 연결 관리
WebSocket 통신 처리
미디어 스트림 처리

join(): 룸 참가 및 WebSocket 연결
onRoomJoined(): WebRTC 연결 설정
handleAnswer(): 서버로부터의 answer 처리
handleRemoteIceCandidate(): ICE candidate 처리

ws: WebSocket 연결
peerConnection: WebRTC 연결
localStream: 로컬 미디어 스트림
*/


// 전역 변수 선언
let ws = null;                    // WebSocket 연결 객체
let peerConnection = null;        // WebRTC 연결 객체
let roomId = null;                // 현재 참가한 룸 ID
let localStream = null;           // 로컬 비디오/오디오 스트림

// 페이지 로드 완료 시 실행2
window.onload = function() {
    // UI 요소 가져오기
    const joinBtn = document.getElementById('joinBtn');
    const leaveBtn = document.getElementById('leaveBtn');
    const roomIdInput = document.getElementById('roomId');

    // 참가 버튼 클릭 이벤트 처리 (1)
    joinBtn.onclick = () => {
        roomId = roomIdInput.value;
        if (!roomId) {
            alert('회의실 ID를 입력해주세요.');
            return;
        }
        join();  // 룸 참가 함수 호출
    };

    // 종료 버튼 클릭 이벤트 처리
    leaveBtn.onclick = () => {
        leave();  // 룸 종료 함수 호출
    };
};

// 페이지 종료 전 실행
window.onbeforeunload = function() {
    leave();  // 룸 종료 함수 호출
};

// 룸 참가 함수 (2)
function join() {
    // 기존 WebSocket 연결이 있다면 종료
    if (ws) {
        ws.close();
    }

    // WebSocket 연결 생성 (3)
    ws = new WebSocket('ws://' + location.host + '/signal');

    // WebSocket 연결 성공 시
    ws.onopen = function() {
        console.log('WebSocket 연결이 열렸습니다.');
        // 룸 참가 메시지 전송
        const message = {
            type: 'joinRoom',
            roomId: roomId
        };
        sendMessage(message); // 룸 참가 메시지 전송 (4)
    };

    // WebSocket 에러 발생 시
    ws.onerror = function(error) {
        console.error('WebSocket 에러:', error);
    };

    // WebSocket 메시지 수신 시 (4)에서 메시지 보낸 거 역기서 받음
    ws.onmessage = function(message) {
        const parsedMessage = JSON.parse(message.data);
        console.info('Received message:', parsedMessage);

        // 메시지 타입에 따른 처리
        switch (parsedMessage.type) {
            case 'roomJoined':  // 룸 참가 성공
                onRoomJoined();
                break;
            case 'answer':      // WebRTC answer 수신, 클라가 오퍼보냈으니 서버가 처리하고 앤서 보낸다?인듯??
                handleAnswer(parsedMessage.sdpAnswer); // 얘로가서
                break;
            case 'iceCandidate':  // ICE candidate 수신
                handleRemoteIceCandidate(parsedMessage.candidate);
                break;
            case 'error':       // 에러 메시지
                alert(parsedMessage.message);
                break;
        }
    };

    // WebSocket 연결 종료 시
    ws.onclose = function() {
        if (peerConnection) {
            peerConnection.close();
            peerConnection = null;
        }
    };
}

// 룸 참가 성공 시 실행 (5)
function onRoomJoined() {
    // 미디어 제약 조건 설정
    const constraints = {
        audio: true,
        video: {
            width: 640,
            framerate: 15
        }
    };

    // WebRTC 설정
    const configuration = {
        iceServers: [
            { urls: 'stun:stun.l.google.com:19302' }  // STUN 서버 설정 <<? 맞겠지뭐
        ]
    };

    // WebRTC 연결 생성 (6)
    peerConnection = new RTCPeerConnection(configuration);
    
    // 원격 스트림 수신 처리
    peerConnection.ontrack = function(event) {
        console.log('Remote track received:', event);
        const remoteVideo = document.getElementById('remoteVideo');
        if (event.streams && event.streams[0]) {
            remoteVideo.srcObject = event.streams[0];
        }
    };

    // ICE candidate 생성 시
    peerConnection.onicecandidate = function(event) {
        if (event.candidate) {
            // ICE candidate를 서버로 전송
            sendMessage({
                type: 'iceCandidate',
                candidate: event.candidate
            });
        }
    };

    // ICE 연결 상태 변경 시
    peerConnection.oniceconnectionstatechange = function() {
        console.log('ICE connection state:', peerConnection.iceConnectionState);
    };
    
    // 로컬 미디어 스트림 가져오기 (7) (권한설정)
    navigator.mediaDevices.getUserMedia(constraints) // onroomjoined 호출하면 결국 일로옴
        .then(stream => {
            // 로컬 스트림 저장 및 비디오 표시
            localStream = stream;
            document.getElementById('localVideo').srcObject = stream;
            
            // WebRTC 연결에 트랙 추가
            stream.getTracks().forEach(track => 
                peerConnection.addTrack(track, stream));
            
            // offer 생성
            return peerConnection.createOffer(); //여기서만듦 **************************************
        })
        .then(offer => {
            // 로컬 description 설정 (8) (offer 생성 및 전송)
            return peerConnection.setLocalDescription(offer);
        })
        .then(() => {
            // offer를 서버로 전송 (9)
            const message = {
                type: 'offer',
                sdpOffer: peerConnection.localDescription.sdp
            };
            sendMessage(message); // 서버가 이 offer 메시지를 받아서 처리할거임 -> line 87 case answer
        })
        .catch(error => {
            console.error('Error creating WebRTC peer:', error);
        });
}

// Answer 처리 함수
function handleAnswer(sdpAnswer) {
    const answer = new RTCSessionDescription({
        type: 'answer',
        sdp: sdpAnswer
    });
    // 원격 description 설정
    peerConnection.setRemoteDescription(answer)
        .catch(error => {
            console.error('Error setting remote description:', error);
        });
}

// 원격 ICE candidate 처리 함수
function handleRemoteIceCandidate(candidate) {
    if (peerConnection) {
        // ICE candidate 추가
        peerConnection.addIceCandidate(new RTCIceCandidate(candidate))
            .catch(error => {
                console.error('Error adding ICE candidate:', error);
            });
    }
}

// 룸 종료 함수
function leave() {
    // 로컬 스트림 정리
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }
    
    // WebRTC 연결 종료
    if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
    }

    // WebSocket 연결 종료
    if (ws) {
        sendMessage({ type: 'leaveRoom' });
        ws.close();
        ws = null;
    }

    // 비디오 요소 초기화
    document.getElementById('localVideo').srcObject = null;
    document.getElementById('remoteVideo').srcObject = null;
}

// WebSocket 메시지 전송 함수
function sendMessage(message) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        const jsonMessage = JSON.stringify(message);
        console.log('Sending message:', jsonMessage);
        ws.send(jsonMessage);
    } else {
        console.error('WebSocket is not connected');
    }
} 