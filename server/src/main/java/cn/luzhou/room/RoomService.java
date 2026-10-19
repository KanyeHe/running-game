package cn.luzhou.room;

import java.util.*;
import java.util.concurrent.*;
import java.security.SecureRandom;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import cn.luzhou.rules.*;
import cn.luzhou.game.*;
import cn.luzhou.store.StateStore;

@Service
public class RoomService {
    private final StateStore store;private final ObjectMapper mapper;
    private final Map<String,Object> locks=new ConcurrentHashMap<>();
    private final CardCombinationFinder finder=CardCombinationFinder.demo();
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Consumer<Map<String,Object>>> listeners=new ConcurrentHashMap<>();
    public RoomService(StateStore store,ObjectMapper mapper){this.store=store;this.mapper=mapper;}
    public StateStore.Identity login(String name) {
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        var id=new StateStore.Identity(UUID.randomUUID().toString(),Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),name);
        store.saveIdentity(id);return id;
    }
    public StateStore.Identity authenticate(String token){return store.authenticate(token).orElseThrow(()->new IllegalArgumentException("UNAUTHORIZED"));}
    public void listen(String connectionId,Consumer<Map<String,Object>> consumer){listeners.put(connectionId,consumer);}
    public void unlisten(String id){listeners.remove(id);}
    private final Map<String,String> connectionPlayers=new ConcurrentHashMap<>();
    private final Map<String,String> connectionRooms=new ConcurrentHashMap<>();
    public void connected(String connection,String player){connectionPlayers.put(connection,player);}
    public void disconnected(String connection) {
        String player=connectionPlayers.remove(connection),id=connectionRooms.remove(connection);unlisten(connection);
        if(player==null||id==null)return;
        synchronized(locks.computeIfAbsent(id,k->new Object())) {
            Room r=copy(require(id));
            boolean online=connectionRooms.entrySet().stream().anyMatch(e->e.getValue().equals(id)&&player.equals(connectionPlayers.get(e.getKey())));
            r.online.put(player,online);r.version++;store.save(r);broadcast(r,"PLAYER_DISCONNECTED");
        }
    }
    public Map<String,Object> command(String connection,String player,String type,String requestId,long seq,String roomId,long expected,Map<String,String> data) {
        if(type.equals("CREATE_ROOM")) {
            // Client supplies a stable UUID room id, making create retries idempotent under the same lock.
            if(roomId==null||!roomId.matches("[a-zA-Z0-9-]{6,64}"))throw new IllegalArgumentException("INVALID_ROOM_ID");
        }
        if(roomId==null)throw new IllegalArgumentException("ROOM_REQUIRED");
        synchronized(locks.computeIfAbsent(roomId,k->new Object())) {
            Room current=store.load(roomId);
            if(current==null&&!type.equals("CREATE_ROOM"))throw new IllegalArgumentException("ROOM_NOT_FOUND");
            Room r=current==null?new Room():copy(current);r.id=roomId;
            if(type.equals("SNAPSHOT")||type.equals("HINT")) {
                requireMember(r,player);connectionRooms.put(connection,roomId);return view(r,player,type,requestId);
            }
            String receiptKey=player+":"+seq;
            String fingerprint=type+":"+expected+":"+new TreeMap<>(data);
            Room.Receipt old=r.receipts.get(receiptKey);
            if(old!=null) {
                if(!old.requestId().equals(requestId)||!old.fingerprint().equals(fingerprint))throw new IllegalArgumentException("SEQUENCE_CONFLICT");
                connectionRooms.put(connection,roomId);
                Map<String,Object> response=view(r,player,"ACK",requestId);response.put("acceptedVersion",old.acceptedVersion());return response;
            }
            if(seq<=r.lastSequences.getOrDefault(player,0L))throw new IllegalArgumentException("OLD_SEQUENCE");
            if(!type.equals("JOIN_ROOM")&&!type.equals("RECONNECT")&&expected!=r.version)throw new IllegalArgumentException("STALE_VERSION");
            switch(type) {
                case "CREATE_ROOM" -> {if(current!=null)throw new IllegalArgumentException("ROOM_EXISTS");r.players.add(player);r.scorePlan=data.getOrDefault("scorePlan","low");if(!Set.of("low","high").contains(r.scorePlan))throw new IllegalArgumentException("INVALID_SCORE_PLAN");}
                case "JOIN_ROOM" -> {
                    if(!r.players.contains(player)){if(r.players.size()>=4||r.game!=null)throw new IllegalArgumentException("ROOM_FULL");r.players.add(player);}
                }
                case "READY" -> {
                    requireMember(r,player);
                    if(r.game!=null&&r.game.winner<0)throw new IllegalArgumentException("GAME_RUNNING");
                    r.ready.add(player);
                    if(r.players.size()==4&&r.ready.size()==4){Integer previous=r.game==null?null:r.game.winner;r.game=Game.deal(random,previous,r.scorePlan.equals("high")?ScoreRuleConfig.high():ScoreRuleConfig.low());r.gameSerial++;r.ready.clear();}
                }
                case "PLAY" -> {
                    requireMember(r,player);if(r.game==null)throw new IllegalArgumentException("GAME_NOT_STARTED");
                    r.game.play(r.players.indexOf(player),data.get("combinationKey"),r.game.version,finder);r.game.assertConservation();
                    Combination played=r.game.events.stream().filter(e->e.combination()!=null).reduce((a,b)->b).orElseThrow().combination();
                    r.operations.add(new Room.Operation(r.gameSerial,requestId,seq,expected,r.version+1,player,played));
                }
                case "RECONNECT" -> requireMember(r,player);
                default -> throw new IllegalArgumentException("UNKNOWN_TYPE");
            }
            for(String member:r.players)r.online.put(member,member.equals(player)||connectionRooms.entrySet().stream()
                .anyMatch(e->e.getValue().equals(r.id)&&member.equals(connectionPlayers.get(e.getKey()))));
            r.version++;
            r.lastSequences.put(player,seq);r.receipts.put(receiptKey,new Room.Receipt(requestId,fingerprint,r.version));
            while(r.receipts.size()>512)r.receipts.remove(r.receipts.keySet().iterator().next());
            store.save(r);connectionRooms.put(connection,roomId);broadcast(r,type);
            return view(r,player,"ACK",requestId);
        }
    }
    private Room copy(Room room){return mapper.convertValue(room,Room.class);}
    private Room require(String id){Room r=store.load(id);if(r==null)throw new IllegalArgumentException("ROOM_NOT_FOUND");return r;}
    private void requireMember(Room r,String player){if(!r.players.contains(player))throw new IllegalArgumentException("NOT_IN_ROOM");}
    private void broadcast(Room r,String type) {
        connectionRooms.forEach((id,room)->{if(room.equals(r.id)){var consumer=listeners.get(id);if(consumer!=null)try {consumer.accept(view(r,connectionPlayers.get(id),type,null));}catch(RuntimeException ignored){/* Transport closes independently; committed state remains authoritative. */}}});
    }
    public Map<String,Object> view(Room r,String player,String event,String request) {
        requireMember(r,player);Map<String,Object> v=new LinkedHashMap<>();
        v.put("type",event);v.put("requestId",request);v.put("roomId",r.id);v.put("version",r.version);v.put("playerId",player);
        v.put("players",r.players);v.put("ready",r.ready);v.put("online",r.online);v.put("policy","demo-v1");
        v.put("gameSerial",r.gameSerial);
        if(r.game!=null) {
            Game g=r.game;int seat=r.players.indexOf(player);
            v.put("hand",g.hands.get(seat));v.put("counts",g.hands.stream().map(List::size).toList());
            v.put("turn",g.turn);v.put("table",g.table);v.put("trick",g.trick);v.put("winner",g.winner);v.put("scores",g.scores);
            v.put("deadline",g.deadline);v.put("serverTime",System.currentTimeMillis());
            v.put("legal",g.legal(seat,finder));v.put("events",g.events.stream().skip(Math.max(0,g.events.size()-12)).toList());
        }
        return v;
    }
}
