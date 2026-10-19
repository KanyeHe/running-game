package cn.luzhou.game;

import java.util.*;
public record ScoreRuleConfig(List<Integer> tiers) {
    public ScoreRuleConfig { tiers=List.copyOf(tiers);if(tiers.size()!=5||tiers.stream().anyMatch(v->v<0))throw new IllegalArgumentException("INVALID_SCORE_CONFIG"); }
    public static ScoreRuleConfig low(){return new ScoreRuleConfig(List.of(0,1,2,4,6));}
    public static ScoreRuleConfig high(){return new ScoreRuleConfig(List.of(0,3,5,10,15));}
    public int forRemaining(int n){return tiers.get(n<=1?0:n<=5?1:n<=9?2:n<=12?3:4);}
}
