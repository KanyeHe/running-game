package cn.luzhou.game;

import java.util.*;
import cn.luzhou.rules.*;
public final class BombScoreLedger {
    public record Entry(long id,long trick,int player,int perOpponent,Long reverses,String combination) {}
    public final List<Entry> entries=new ArrayList<>();
    public void record(long trick,int player,Combination bomb,BombPolicy policy,ScoreRuleConfig config,int[] scores) {
        Entry active=entries.stream().filter(e->e.trick()==trick&&e.reverses()==null)
            .filter(e->entries.stream().noneMatch(r->Objects.equals(r.reverses(),e.id()))).findFirst().orElse(null);
        if(active!=null) {
            transfer(scores,active.player(),-active.perOpponent());
            entries.add(new Entry(entries.size()+1L,trick,active.player(),-active.perOpponent(),active.id(),active.combination()));
        }
        int amount=config.tiers().get(policy.isTwoBomb(bomb)?3:2);
        transfer(scores,player,amount);
        entries.add(new Entry(entries.size()+1L,trick,player,amount,null,bomb.key()));
    }
    private static void transfer(int[] scores,int winner,int amount) {
        for(int p=0;p<4;p++) scores[p]+=p==winner?3*amount:-amount;
    }
}
