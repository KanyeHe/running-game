package cn.luzhou.rules;

public interface BombPolicy {
  /** Demonstration only: four of one rank, including jokers; natural/soft equal, compare rank. */
  static BombPolicy demo() {
    return new BombPolicy() {
      public boolean accepts(int rank, int count, int wilds) {
        return count == 4;
      }

      public boolean beats(Combination a, Combination b) {
        return a.value() > b.value();
      }

      public boolean isTwoBomb(Combination b) {
        return b.bodyRank() == 2;
      }
    };
  }

  static BombPolicy unconfirmed() {
    return new BombPolicy() {
      public boolean accepts(int r, int c, int w) {
        return c == 4 && w == 0;
      }

      public boolean beats(Combination a, Combination b) {
        return false;
      }

      public boolean isTwoBomb(Combination b) {
        throw new IllegalStateException("TWO_BOMB_UNCONFIRMED");
      }
    };
  }

  boolean accepts(int rank, int count, int wildCount);

  boolean beats(Combination candidate, Combination previous);

  boolean isTwoBomb(Combination bomb);
}
