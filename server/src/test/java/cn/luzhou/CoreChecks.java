package cn.luzhou;

import java.util.*;
import cn.luzhou.rules.*;
import cn.luzhou.game.*;
import static cn.luzhou.rules.Combination.Type.*;
import static cn.luzhou.rules.CardCombinationFinder.Lead.*;

/** Shared by JUnit and the dependency-free JDK25 runner. Assertions never depend on -ea. */
public final class CoreChecks {
    private static final CardCombinationFinder F=CardCombinationFinder.demo();
    private static int checks;
    static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    static List<Card> cards(int... ranks){Map<Integer,Integer> suits=new HashMap<>();List<Card> out=new ArrayList<>();for(int r:ranks){if(r>=52)out.add(new Card(r));else{int s=suits.getOrDefault(r,0);out.add(new Card(s*13+r-1));suits.put(r,s+1);}}return out;}
    static Combination combo(Combination.Type type,int... ranks){return F.find(cards(ranks),null,FREE).stream().filter(c->c.type()==type&&c.cardIds().size()==ranks.length).findFirst().orElseThrow(()->new AssertionError(type+Arrays.toString(ranks)));}
    static void rejects(Runnable action,String name){try{action.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){checks++;}}
    public static void rules() {
        check(F.beats(combo(SINGLE,2),combo(SINGLE,1)),"2>A");
        check(F.beats(combo(PAIR,1,1),combo(PAIR,13,13)),"AA>KK");
        check(F.beats(combo(TRIPLE,5,5,5),combo(TRIPLE,4,4,4)),"triple comparison");
        check(!F.beats(combo(TRIPLE_PAIR,5,5,5,6,6),combo(TRIPLE_SINGLES,4,4,4,6,7)),"attachment consistency");
        check(F.beats(combo(STRAIGHT,2,3,4,5,6),combo(STRAIGHT,1,2,3,4,5)),"23456>12345");
        check(F.beats(combo(STRAIGHT,3,4,5,6,7),combo(STRAIGHT,2,3,4,5,6)),"34567>23456");
        check(!F.beats(combo(STRAIGHT,2,3,4,5,6,7),combo(STRAIGHT,1,2,3,4,5)),"straight length");
        check(F.beats(combo(PAIR_RUN,2,2,3,3),combo(PAIR_RUN,1,1,2,2)),"2233>AA22");
        check(F.beats(combo(PAIR_RUN,3,3,4,4),combo(PAIR_RUN,2,2,3,3)),"3344>2233");
        check(F.beats(combo(PLANE,2,2,2,3,3,3),combo(PLANE,1,1,1,2,2,2)),"222333>AAA222");
        combo(PLANE,3,3,3,4,4,4,5,5,5);
        combo(PLANE_SINGLES,3,3,3,4,4,4,6,7,8,9);
        combo(PLANE_PAIRS,3,3,3,4,4,4,6,6,7,7);
        check(F.find(cards(3,3,3,4,4,4,6,6,7,8),null,FREE).stream().noneMatch(c->c.cardIds().size()==10),"mixed wings forbidden");
        check(F.find(cards(6,6,6),null,FREE).stream().noneMatch(c->c.type()==PLANE),"666 not plane");
        check(F.beats(combo(BOMB,4,4,4,4),combo(SINGLE,2)),"bomb beats normal");
        for(var type:List.of(SINGLE,PAIR,TRIPLE,TRIPLE_SINGLES,TRIPLE_PAIR,STRAIGHT,PAIR_RUN,PLANE,PLANE_SINGLES,PLANE_PAIRS,BOMB)) {
            int[] ranks=switch(type){case SINGLE->new int[]{52};case PAIR->new int[]{4,52};case TRIPLE->new int[]{4,4,52};case TRIPLE_SINGLES->new int[]{4,4,52,6,7};case TRIPLE_PAIR->new int[]{4,4,52,6,6};case STRAIGHT->new int[]{1,2,3,4,52};case PAIR_RUN->new int[]{1,1,2,52};case PLANE->new int[]{3,3,3,4,4,52};case PLANE_SINGLES->new int[]{3,3,3,4,4,52,6,7,8,9};case PLANE_PAIRS->new int[]{3,3,3,4,4,52,6,6,7,7};case BOMB->new int[]{13,13,13,52};};
            Combination c=combo(type,ranks);check(c.cardIds().contains(52)&&c.wildRanks().containsKey(52),"wild "+type);
        }
        combo(TRIPLE,5,52,53);
        List<Combination> choices=F.find(cards(2,52,13,13,13),combo(SINGLE,1),FREE);
        check(choices.stream().anyMatch(c->c.type()==SINGLE)&&choices.stream().anyMatch(c->c.type()==BOMB),"ordinary and bomb freedom");
        check(F.find(cards(2,52,3,3),null,WINNER_FIRST).stream().noneMatch(c->c.type()==SINGLE&&(c.bodyRank()==2||c.cardIds().contains(52))||c.type()==PAIR&&c.bodyRank()==2),"winner restrictions");
        check(!F.find(cards(1,2,3,4,5),null,WINNER_FIRST).stream().filter(c->c.type()==STRAIGHT).toList().isEmpty(),"winner can lead straight containing 2");
        check(F.find(cards(3,4,52),null,FIRST_SPADE_THREE).stream().allMatch(c->c.cardIds().contains(2)),"physical spade3 required");
        check(F.find(List.of(new Card(15),new Card(52)),null,FIRST_SPADE_THREE).isEmpty(),"logical spade3 insufficient");
        check(!WildcardPolicy.demo().allows(SINGLE,Map.of(52,14)),"illegal wild rank");
        check(!WildcardPolicy.unconfirmed().allows(SINGLE,Map.of(52,2)),"unconfirmed strict placeholder");
        check(!BombPolicy.unconfirmed().accepts(5,4,1),"unconfirmed soft bomb placeholder");
        rejects(()->F.find(List.of(new Card(2),new Card(2)),null,FREE),"duplicate ID rejected");
        check(choices.equals(F.find(cards(2,52,13,13,13),combo(SINGLE,1),FREE)),"stable enumeration");
        check(choices.stream().map(Combination::key).distinct().count()==choices.size(),"deduplication");
    }
    public static void state() {
        Game g=new Game();g.firstAction=false;g.hands=new ArrayList<>();
        g.hands.add(new ArrayList<>(cards(3,7,11)));g.hands.add(new ArrayList<>(cards(4,8,12)));
        g.hands.add(new ArrayList<>(cards(5,9,13)));g.hands.add(new ArrayList<>(cards(6,10,1)));
        for(int i=0;i<8;i++){int p=i%4;check(g.turn==p,"multi-loop turn "+i);var c=g.legal(p,F).stream().filter(x->x.type()==SINGLE).findFirst().orElseThrow();g.play(p,c.key(),g.version,F);}
        check(g.trick==1&&g.events.stream().filter(e->e.type().equals("PLAYED")).count()==8,"two complete loops same trick");
        check(g.consecutiveUnable==0,"successful play resets unable");
        rejects(()->g.play(g.turn,"invalid",g.version,F),"illegal key");
        rejects(()->g.play(g.turn,"invalid",g.version-1,F),"stale game version");
        rejects(()->g.play((g.turn+1)%4,"invalid",g.version,F),"wrong turn");
        Game t=new Game();t.firstAction=false;t.hands=new ArrayList<>(List.of(new ArrayList<>(cards(2,3)),new ArrayList<>(cards(4)),new ArrayList<>(cards(5)),new ArrayList<>(cards(6))));
        t.play(0,combo(SINGLE,2).key(),t.version,F);
        check(t.turn==0&&t.table==null&&t.trick==2,"three skips return leader");
        check(t.events.stream().filter(e->e.type().equals("AUTO_SKIPPED")).count()==3,"automatic skip events");
        t.play(0,combo(SINGLE,3).key(),t.version,F);check(t.winner==0,"immediate finish");
        check(Arrays.stream(t.scores).sum()==0,"settlement zero sum");
        rejects(()->t.play(1,"invalid",t.version,F),"no duplicate settlement");
        Game timeout=Game.deal(new Random(4),null,ScoreRuleConfig.low());long v=timeout.version;
        timeout.timeout(F,TimeoutPolicy.awaitPlayer(),Long.MAX_VALUE);check(timeout.version==v,"timeout does not pass");
        timeout.timeout(F,TimeoutPolicy.demoTrustee(),Long.MAX_VALUE);check(timeout.version>v,"opt-in legal trustee");
        int[] scores=new int[4];BombScoreLedger ledger=new BombScoreLedger();
        ledger.record(1,0,combo(BOMB,4,4,4,4),BombPolicy.demo(),ScoreRuleConfig.low(),scores);
        check(Arrays.equals(scores,new int[]{6,-2,-2,-2}),"immediate bomb scoring");
        ledger.record(1,1,combo(BOMB,2,2,2,2),BombPolicy.demo(),ScoreRuleConfig.low(),scores);
        check(Arrays.equals(scores,new int[]{-4,12,-4,-4}),"bomb reversal and replacement");
        check(ledger.entries.size()==3&&ledger.entries.get(1).reverses()==1L,"auditable reversal");
        ledger.record(2,2,combo(BOMB,5,5,5,5),BombPolicy.demo(),ScoreRuleConfig.high(),scores);
        check(Arrays.stream(scores).sum()==0&&ledger.entries.size()==4,"independent tricks");
        check(ScoreRuleConfig.low().forRemaining(1)==0&&ScoreRuleConfig.low().forRemaining(13)==6&&ScoreRuleConfig.high().forRemaining(10)==10,"score tiers");
    }
    public static void simulations(int seeds) {
        for(int seed=0;seed<seeds;seed++) {
            Game g=Game.deal(new Random(seed),null,seed%2==0?ScoreRuleConfig.low():ScoreRuleConfig.high());
            List<String> log=new ArrayList<>();Random choice=new Random(seed+123);
            try {
                g.assertConservation();int steps=0;
                while(g.winner<0) {
                    var legal=g.legal(g.turn,F);check(!legal.isEmpty(),"seed "+seed+" legal turn");
                    Combination c=legal.get(choice.nextInt(legal.size()));int p=g.turn,before=g.hands.get(p).size();
                    Set<Integer> owned=new HashSet<>();g.hands.get(p).forEach(x->owned.add(x.id()));
                    check(owned.containsAll(c.cardIds()),"ownership");log.add(p+":"+c.key());
                    g.play(p,c.key(),g.version,F);check(g.hands.get(p).size()==before-c.cardIds().size(),"removal count");
                    g.assertConservation();check(++steps<=52,"termination");
                }
                check(g.hands.get(g.winner).isEmpty(),"winner empty");
            }catch(Throwable e){throw new AssertionError("seed="+seed+" actions="+log,e);}
        }
    }
    public static void main(String[] args){rules();state();simulations(args.length==0?30:Integer.parseInt(args[0]));System.out.println("PASS "+checks+" checks; JDK "+Runtime.version());}
}
