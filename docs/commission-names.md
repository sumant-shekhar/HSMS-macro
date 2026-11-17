# Commission name comparison

This compares the 11 names listed for SkyHanni's mob-highlighting map in [skyhanni-findings.md](./skyhanni-findings.md) with the display names declared by MightyMiner's legacy [`Commission` enum](../reference/07_macros/macro/impl/CommissionMacro/Commission.java). It does not add or infer enum entries.

## SkyHanni names missing from our enum

- Star Sentry Puncher
- Golden Goblin Slayer
- Treasure Hoarder Puncher
- Automaton Slayer
- Team Treasurite Member Slayer
- Yog Slayer
- Thyst Slayer
- Corleone Slayer
- Sludge Slayer

The two names present in both lists are `Goblin Slayer` and `Glacite Walker Slayer`.

## Our enum names missing from SkyHanni's list

- Mithril Miner
- Titanium Miner
- Upper Mines Mithril
- Upper Mines Titanium
- Royal Mines Mithril
- Royal Mines Titanium
- Lava Springs Mithril
- Lava Springs Titanium
- Cliffside Veins Mithril
- Cliffside Veins Titanium
- Rampart's Quarry Mithril
- Rampart's Quarry Titanium
- Mines Slayer
- Claim Commission
- Refuel Drill

The comparison uses declared display-name strings, not Java enum constant identifiers. `Claim Commission` and `Refuel Drill` are included because they are declared in our enum even though they are operational entries rather than ordinary mining commission names.
