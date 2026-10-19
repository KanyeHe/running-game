package cn.luzhou;

import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.luzhou.room.*;
import cn.luzhou.store.*;
import cn.luzhou.rules.*;

public final class RoomChecks {
    public static void run() throws Exception {
        ObjectMapper json=new ObjectMapper();MemoryStore store=new MemoryStore();RoomService service=new RoomService(store,json);
        String room="test-room";String[] players=new String[4];long[] seq=new long[4];long version=1;
        for(int i=0;i<4;i++) {
            var identity=service.login("Player"+i);players[i]=identity.playerId();
            CoreChecks.check(service.authenticate(identity.token()).playerId().equals(players[i]),"token auth");
            service.connected("c"+i,players[i]);
            var response=service.command("c"+i,players[i],i==0?"CREATE_ROOM":"JOIN_ROOM","join"+i,++seq[i],room,version,Map.of());
            version=((Number)response.get("version")).longValue();
        }
        for(int i=0;i<4;i++) {
            var response=service.command("c"+i,players[i],"READY","ready"+i,++seq[i],room,version,Map.of());
            version=((Number)response.get("version")).longValue();
        }
        Room saved=store.load(room);int turn=saved.game.turn;long before=saved.version;
        var snapshot=service.command("c"+turn,players[turn],"SNAPSHOT","view",999,room,0,Map.of());
        String publicJson=json.writeValueAsString(snapshot);
        CoreChecks.check(!publicJson.contains("discarded")&&!publicJson.contains("\"hands\"")&&!publicJson.contains("token"),"private information projection");
        CoreChecks.check(((List<?>)snapshot.get("hand")).size()==13,"own hand only");
        var legal=saved.game.legal(turn,CardCombinationFinder.demo());String key=legal.getFirst().key();
        long commandSeq=++seq[turn];
        Map<String,String> data=Map.of("combinationKey",key);
        var first=service.command("c"+turn,players[turn],"PLAY","play-once",commandSeq,room,before,data);
        long after=store.load(room).version;int count=store.load(room).game.played.size();
        service.command("c"+turn,players[turn],"PLAY","play-once",commandSeq,room,before,data);
        CoreChecks.check(store.load(room).version==after&&store.load(room).game.played.size()==count,"idempotent play");
        CoreChecks.rejects(()->service.command("c"+turn,players[turn],"PLAY","changed",commandSeq,room,before,data),"sequence conflict");
        CoreChecks.rejects(()->service.command("outsider","other","SNAPSHOT","x",1,room,0,Map.of()),"unauthorized snapshot");
        // Actual concurrent requests for the same version: exactly one commit.
        Room next=store.load(room);int p=next.game.turn;long expected=next.version;
        String nextKey=next.game.legal(p,CardCombinationFinder.demo()).getFirst().key();
        CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> results=new ArrayList<>();
        try(var pool=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<2;i++){long s=++seq[p];String request="race"+i;
                results.add(pool.submit(()->{start.await();try{service.command("c"+p,players[p],"PLAY",request,s,room,expected,Map.of("combinationKey",nextKey));return true;}catch(IllegalArgumentException rejected){return false;}}));}
            start.countDown();int accepted=0;for(var result:results)if(result.get())accepted++;
            CoreChecks.check(accepted==1&&store.load(room).version==expected+1,"serialized concurrent play");
        }
        // New service instance restores complete snapshot, receipts and membership from the store.
        RoomService restarted=new RoomService(store,json);restarted.connected("recovered",players[0]);
        var recovered=restarted.command("recovered",players[0],"RECONNECT","recover",100,room,0,Map.of());
        CoreChecks.check(recovered.get("roomId").equals(room)&&((List<?>)recovered.get("hand")).size()==store.load(room).game.hands.get(0).size(),"reconnect snapshot recovery");
        CoreChecks.check(store.load(room).online.get(players[0])&&!store.load(room).online.get(players[1]),"restart does not restore stale online flags");
        // Failed storage commit must leave the previously committed in-memory room untouched.
        StateStore failing=new StateStore(){
            public Room load(String id){return store.load(id);}public void save(Room r){throw new IllegalStateException("injected failure");}
            public void saveIdentity(Identity i){store.saveIdentity(i);}public Optional<Identity> authenticate(String token){return store.authenticate(token);}
        };
        RoomService broken=new RoomService(failing,json);long committed=store.load(room).version;
        try {broken.command("b",players[0],"RECONNECT","fail",101,room,0,Map.of());throw new AssertionError("expected store failure");}catch(IllegalStateException expectedFailure){}
        CoreChecks.check(store.load(room).version==committed,"failed persistence rolls back mutation");
        var game=cn.luzhou.game.Game.deal(new Random(5),null,cn.luzhou.game.ScoreRuleConfig.low());
        game.ledger.record(1,0,CoreChecks.combo(Combination.Type.BOMB,5,5,5,5),BombPolicy.demo(),game.scoreConfig,game.scores);
        var restored=json.readValue(json.writeValueAsString(game),cn.luzhou.game.Game.class);
        CoreChecks.check(restored.ledger.entries.size()==1&&Arrays.equals(restored.scores,game.scores),"nonempty ledger snapshot roundtrip");
        System.out.println("PASS room auth/privacy/idempotency/concurrency/recovery/rollback checks");
    }
    public static void main(String[] args)throws Exception{run();}
}
