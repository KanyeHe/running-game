package cn.luzhou.rules;

/** Unconfirmed plane length / wing comparison is kept outside ordinary type comparison. */
public interface PlanePolicy {
  static PlanePolicy demo() {
    return new PlanePolicy() {
      public boolean compatible(int count, int groups, Combination previous) {
        return count == previous.cardIds().size() && groups == previous.groups();
      }

      public boolean beats(Combination a, Combination b) {
        return compatible(a.cardIds().size(), a.groups(), b) && a.value() > b.value();
      }
    };
  }

  boolean compatible(int cardCount, int groups, Combination previous);

  boolean beats(Combination candidate, Combination previous);
}
