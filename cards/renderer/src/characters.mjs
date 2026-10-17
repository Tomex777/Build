/** Bind a character to every existing card template without changing its design. */
export function characterCards(templates, character) {
 if (!Array.isArray(templates) || !templates.length) throw new TypeError('Card templates are required');
 if (!/^[a-z][a-z0-9-]{0,23}$/.test(character?.id ?? '')) throw new TypeError('A short character id is required');
 if (typeof character.name !== 'string' || !character.name.trim()) throw new TypeError('A character name is required');
 if (!character.artwork?.id) throw new TypeError('Character artwork is required');
 return templates.map(template => ({
  ...structuredClone(template),
  id: `${character.id}-${template.id}`,
  name: character.name,
  artwork: character.artwork.id,
  ...(character.subtitle ? {subtitle: character.subtitle} : {}),
  ...(character.description ? {description: character.description} : {})
 }));
}
