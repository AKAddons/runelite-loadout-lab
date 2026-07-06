/**
 * Loadout Lab verification harness (local tool, not part of upstream).
 *
 * Reads scenario vectors from the JSON file named by LOADOUT_LAB_VECTORS,
 * computes the official calculator's numbers for each, and writes them to
 * LOADOUT_LAB_RESULTS. Run:
 *
 *   LOADOUT_LAB_VECTORS=/path/vectors.json \
 *   LOADOUT_LAB_RESULTS=/path/official.json \
 *   corepack yarn jest src/tests/harness/LoadoutLabVectors.test.ts
 *
 * Vector shape: { name, monster, monsterVersion?, gear: [name or [name, version]...],
 *                 spell?, prayers?: string[], skills?: {atk,str,def,ranged,magic,hp,prayer} }
 */
import { describe, expect, test } from '@jest/globals';
import fs from 'fs';
import {
  calculatePlayerVsNpc, findEquipment, findSpell, getTestMonster, getTestPlayer,
} from '@/tests/utils/TestUtils';
import { EquipmentPiece } from '@/types/Player';
import { Prayer } from '@/enums/Prayer';
import { getCombatStylesForCategory } from '@/utils';
import { EquipmentCategory } from '@/enums/EquipmentCategory';

const SLOT_KEYS: Record<string, string> = {
  head: 'head', cape: 'cape', neck: 'neck', ammo: 'ammo', weapon: 'weapon',
  body: 'body', shield: 'shield', legs: 'legs', hands: 'hands', feet: 'feet', ring: 'ring',
};

describe('loadout lab vectors', () => {
  const vectorsPath = process.env.LOADOUT_LAB_VECTORS;
  const resultsPath = process.env.LOADOUT_LAB_RESULTS;
  if (!vectorsPath || !resultsPath) {
    test('skipped (no LOADOUT_LAB_VECTORS)', () => expect(true).toBe(true));
    return;
  }

  test('compute official numbers', () => {
    const vectors = JSON.parse(fs.readFileSync(vectorsPath, 'utf-8'));
    const results: object[] = [];
    for (const v of vectors) {
      try {
        const monster = getTestMonster(v.monster, v.monsterVersion || '');
        const equipment: Record<string, EquipmentPiece> = {};
        for (const entry of v.gear || []) {
          const [name, version] = Array.isArray(entry) ? entry : [entry, ''];
          const piece = findEquipment(name, version);
          const slot = SLOT_KEYS[piece.slot];
          if (slot) {
            equipment[slot] = piece;
          }
        }
        // Prayer names are enum keys: "PIETY", "RIGOUR", "AUGURY", "MYSTIC_VIGOUR".
        const prayers = (v.prayers || [])
          .map((name: string) => Prayer[name as keyof typeof Prayer])
          .filter((p: Prayer | undefined) => p !== undefined);
        // Mirror the Loadout Lab optimizer's semantic: the BEST combat
        // style for this weapon, not the first one in the list.
        const styles = getCombatStylesForCategory(
          equipment.weapon?.category || EquipmentCategory.NONE);
        let best: ReturnType<typeof calculatePlayerVsNpc> | null = null;
        let bestStyle = '';
        for (const style of styles) {
          const player = getTestPlayer(monster, {
            equipment,
            prayers,
            style,
            skills: v.skills || undefined,
            spell: v.spell ? findSpell(v.spell) : undefined,
            buffs: v.markOfDarkness ? { markOfDarknessSpell: true } : undefined,
          });
          const r = calculatePlayerVsNpc(monster, player);
          if (!best || r.dps > best.dps) {
            best = r;
            bestStyle = `${style.type}/${style.stance}`;
          }
        }
        if (!best) {
          throw new Error('no styles for weapon');
        }
        results.push({
          name: v.name,
          dps: best.dps,
          maxHit: best.maxHit,
          accuracy: best.accuracy,
          attackRoll: best.maxAttackRoll,
          npcDefRoll: best.npcDefRoll,
          style: bestStyle,
        });
      } catch (e) {
        results.push({ name: v.name, error: String(e) });
      }
    }
    fs.writeFileSync(resultsPath, JSON.stringify(results, null, 1));
    expect(results.length).toBeGreaterThan(0);
  });
});
