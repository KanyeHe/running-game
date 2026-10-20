package cn.luzhou.game;

import cn.luzhou.rules.*;
import java.util.*;

public interface TimeoutPolicy {
  static TimeoutPolicy awaitPlayer() {
    return legal -> Optional.empty();
  }

  /** Opt-in demonstration trustee always chooses a legal candidate, never passes. */
  static TimeoutPolicy demoTrustee() {
    return legal -> legal.stream().findFirst();
  }

  Optional<Combination> choose(List<Combination> legal);
}
