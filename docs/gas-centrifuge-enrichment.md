# Gas centrifuge enrichment campaigns

Gas centrifuges remain single-stage machines: place them in a line so the product pseudo-fluid from one machine enters the next. UF6, electricity, pseudo-fluid transfer, and ordinary item extraction can be automated. Campaign choice, installing/removing a **centrifuge element**, starting, controlled stopping, and major maintenance are operator actions.

## Civilian LEU fuel

Select **CIVILIAN**, install a centrifuge element in each machine, and use a two-machine cascade. A complete campaign starts with 1,200 mB UF6 and produces six uranium-fuel nuggets for the existing billet/ingot and reactor-fuel fabrication recipes. It does not require the speed upgrade, U-235, weapon components, or strategic hardware. Plan item output space for depleted-U-238 tails and fluorite. NEI shows the authoritative per-stage feed, product, tails, ticks, power rate, and energy total.

## Strategic HEU / U-235

Select **STRATEGIC** before preparing the batch. This campaign requires four adjacent centrifuge stages, 1,200 mB UF6 at its first stage, speed/advanced-rotor hardware on every stage, sustained 600 HE/t operation, and substantially more elapsed work and tails capacity. Intermediate LEUF6 and MEUF6 are internal cascade streams, not invented product items. The terminal stage produces the existing U-235 nugget used by strategic component recipes.

The campaign identifier locks as soon as feed is reserved. It prevents campaign or fluid-ID changes until completion, so a prepared batch cannot be substituted. A brief power outage or blocked pseudo-fluid/item output pauses without losing feed or progress; clearing it resumes the same batch. A controlled stop preserves both, but resets the 100-tick spin-up and therefore has a visible energy/time consequence.

## Throughput and maintenance

The speed upgrade is throughput hardware: it approximately halves processing ticks while drawing twice the normal stage power, so it never reduces total energy per completed unit. Strategic enrichment uses it as required rotor capability; civilian enrichment does not. Rotor wear is stored on the centrifuge rather than on many worn item variants. One element lasts many campaigns; when the maintenance threshold is reached, install a replacement and use the manual maintenance control.

Depleted-U-238 tails are emitted separately at each uranium separation stage. They retain their existing uses and are intentionally a low-value by-product, not an enrichment-cost refund. Avoid circular or branching output arrangements: a centrifuge will not feed itself or overwrite a prepared destination batch.
