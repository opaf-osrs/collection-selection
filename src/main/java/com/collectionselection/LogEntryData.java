package com.collectionselection;

import java.util.HashMap;
import java.util.Map;

/**
 * Static reference data for the Collection Selection plugin.
 *
 * PET_TO_ENTRY maps pet item IDs to their collection log entry name.
 * Entry name strings must match exactly what the collection log header widget shows.
 */
public class LogEntryData
{
	public static final Map<Integer, String> PET_TO_ENTRY = new HashMap<>();

	static
	{
		// ── God Wars Dungeon ─────────────────────────────────────────────────
		PET_TO_ENTRY.put(11998, "General Graardor");       // Graardor's baby
		PET_TO_ENTRY.put(11996, "Commander Zilyana");      // Zilyana's baby
		PET_TO_ENTRY.put(12000, "K'ril Tsutsaroth");       // K'ril's baby
		PET_TO_ENTRY.put(11994, "Kree'arra");              // Kree'arra's baby

		// ── Wilderness bosses ────────────────────────────────────────────────
		PET_TO_ENTRY.put(11995, "Chaos Elemental");        // Little Chaos Elemental
		PET_TO_ENTRY.put(11999, "King Black Dragon");      // Prince black dragon
		PET_TO_ENTRY.put(11997, "Callisto");               // Callisto cub (classic)
		PET_TO_ENTRY.put(13181, "Venenatis");              // Venenatis spiderling (classic)
		PET_TO_ENTRY.put(13179, "Vet'ion");                // Vet'ion Jr. (classic)
		PET_TO_ENTRY.put(27686, "Callisto");               // Callisto cub (rework)
		PET_TO_ENTRY.put(27684, "Venenatis");              // Venenatis spiderling (rework)
		PET_TO_ENTRY.put(27682, "Vet'ion");                // Vet'ion Jr. (rework)

		// ── Slayer bosses ────────────────────────────────────────────────────
		PET_TO_ENTRY.put(12921, "Zulrah");                 // Snakeling
		PET_TO_ENTRY.put(13247, "Cerberus");               // Hellpuppy
		PET_TO_ENTRY.put(12944, "Kraken");                 // Tiny kraken
		PET_TO_ENTRY.put(12648, "Thermonuclear Smoke Devil"); // Smoke devil
		PET_TO_ENTRY.put(21061, "Grotesque Guardians");    // Noon
		PET_TO_ENTRY.put(13262, "Abyssal Sire");           // Abyssal orphan
		PET_TO_ENTRY.put(22746, "Alchemical Hydra");       // Ikkle Hydra

		// ── Other bosses ─────────────────────────────────────────────────────
		PET_TO_ENTRY.put(12647, "Kalphite Queen");         // Kalphite princess
		PET_TO_ENTRY.put(12646, "Giant Mole");             // Baby mole
		PET_TO_ENTRY.put(12816, "Corporeal Beast");        // Corporeal critter
		PET_TO_ENTRY.put(21981, "Vorkath");                // Vorki
		PET_TO_ENTRY.put(22817, "Sarachnis");              // Sraracha
		PET_TO_ENTRY.put(24491, "The Nightmare");          // Little nightmare
		PET_TO_ENTRY.put(24495, "Phosani's Nightmare");    // Little nightmare (phosani)
		PET_TO_ENTRY.put(26348, "Nex");                    // Nexling

		// ── Dagannoth Kings ──────────────────────────────────────────────────
		PET_TO_ENTRY.put(12642, "Dagannoth Kings");        // Dagannoth rex
		PET_TO_ENTRY.put(12643, "Dagannoth Kings");        // Dagannoth prime
		PET_TO_ENTRY.put(12644, "Dagannoth Kings");        // Dagannoth supreme

		// ── Minigames / Fight caves ──────────────────────────────────────────
		PET_TO_ENTRY.put(13225, "TzTok-Jad");             // Baby Jad
		PET_TO_ENTRY.put(21291, "TzKal-Zuk");             // Jal-Nib-Rek

		// ── Raids ────────────────────────────────────────────────────────────
		PET_TO_ENTRY.put(20851, "Chambers of Xeric");     // Olmlet
		PET_TO_ENTRY.put(22473, "Theatre of Blood");      // Lil' Zik
		PET_TO_ENTRY.put(27435, "Tombs of Amascut");      // Tumeken's guardian

		// ── Desert Treasure II bosses ─────────────────────────────────────────
		PET_TO_ENTRY.put(28688, "Duke Sucellus");         // Duke
		PET_TO_ENTRY.put(28690, "The Leviathan");         // Leviathan
		PET_TO_ENTRY.put(28692, "The Whisperer");         // Wisp
		PET_TO_ENTRY.put(28694, "Vardorvis");             // Butch

		// ── Araxxor ──────────────────────────────────────────────────────────
		PET_TO_ENTRY.put(28476, "Araxxor");               // Araxyte hatchling
	}
}
