// Generated from client/assets/scripts; edit shared TypeScript source.
/** Every selectable card and final selection come from the server's current candidate list. */
export class Selection {
    constructor() {
        this.candidates = [];
        this.selected = new Set();
        this.hintIndex = 0;
    }
    reset(candidates) {
        this.candidates = candidates;
        this.selected.clear();
        this.hintIndex = 0;
        this.preferred = undefined;
    }
    enabled(id) {
        return this.candidates.some(c => c.cardIds.includes(id));
    }
    toggle(id) {
        this.preferred = undefined;
        if (this.selected.has(id)) {
            this.selected.delete(id);
            return;
        }
        if (this.candidates.some(c => c.cardIds.includes(id) && [...this.selected].every(s => c.cardIds.includes(s))))
            this.selected.add(id);
    }
    hint() {
        if (!this.candidates.length)
            return undefined;
        const c = this.candidates[this.hintIndex++ % this.candidates.length];
        this.selected = new Set(c.cardIds);
        this.preferred = c;
        return c;
    }
    exact() {
        return this.preferred ?? this.candidates.find(c => c.cardIds.length === this.selected.size && c.cardIds.every(id => this.selected.has(id)));
    }
}
