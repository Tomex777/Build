// A reviewable design proposal, not a user-approved production tier system.
export const TIER_STYLES = Object.freeze({
  F:'vanguard', E:'ivory', D:'voltage', C:'crimson', B:'tidelight',
  A:'nocturne', S:'emberglass', SS:'obsidian', SSS:null, EX:null
});
export function designForRank(rank) {
  if (!Object.hasOwn(TIER_STYLES,rank)) throw Error('Unknown power tier');
  if (!TIER_STYLES[rank]) throw Error(`Tier ${rank} needs its own reviewed design`);
  return TIER_STYLES[rank];
}
export function validateTierStyles(catalogue) {
  if (catalogue.tierPolicy?.mode !== 'power-selects-design') return;
  for (const card of catalogue.cards)
    if (card.designs.length !== 1 || card.designs[0] !== designForRank(card.power.rank))
      throw Error(`Design does not match form tier: ${card.id}`);
}
