package cn.luzhou.game;

import java.util.*;
import cn.luzhou.rules.*;
public interface TimeoutPolicy {
    Optional<Combination> choose(List<Combination> legal);
    static TimeoutPolicy awaitPlayer(){return legal->Optional.empty();}
    /** Opt-in demonstration trustee always chooses a legal candidate, never passes. */
    static TimeoutPolicy demoTrustee(){return legal->legal.stream().findFirst();}
}
