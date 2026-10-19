import { Combination } from './model';

/** Every selectable card and final selection come from the server's current candidate list. */
export class Selection {
  candidates: Combination[] = [];
  selected = new Set<number>();
  hintIndex = 0;
  preferred?: Combination;
  reset(candidates: Combination[]): void { this.candidates = candidates; this.selected.clear(); this.hintIndex = 0; this.preferred = undefined; }
  enabled(id: number): boolean { return this.candidates.some(c => c.cardIds.includes(id)); }
  toggle(id: number): void {
    this.preferred = undefined;
    if (this.selected.has(id)) { this.selected.delete(id); return; }
    if (this.candidates.some(c => c.cardIds.includes(id) && [...this.selected].every(s => c.cardIds.includes(s)))) this.selected.add(id);
  }
  hint(): Combination | undefined {
    if (!this.candidates.length) return undefined;
    const c = this.candidates[this.hintIndex++ % this.candidates.length];
    this.selected = new Set(c.cardIds); this.preferred = c; return c;
  }
  exact(): Combination | undefined {
    return this.preferred ?? this.candidates.find(c => c.cardIds.length === this.selected.size && c.cardIds.every(id => this.selected.has(id)));
  }
}
