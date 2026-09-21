# Water tiering

RNT separates water by process quality:

- **Water** is raw environmental water. Pumps, vanilla containers, melting ice, ore washing, concrete, fracking, and other environmental uses keep this tier.
- **Fresh Water** is treated industrial water. Produce 900 mB plus 100 mB brine from each 1,000 mB of raw water in the Chemical Plant. Boilers, condensers, cooling towers, and general industrial chemistry use it.
- **Light Water** is demineralized reactor water. The Chemical Plant converts fresh water to light water at a 1:1 ratio. Direct-boiling nuclear systems and deuterium extraction require this tier.
- **Borated Water** is the default PWR coolant. Make it from light water and boron powder in the Chemical Plant. Light water remains an alternate PWR coolant, and heavy water remains supported.

PWR heat converts light water, borated water, and heavy water into their matching hot fluids. A heat exchanger returns each hot fluid to its matching cold fluid. Direct-boiling steam condenses to fresh water, which must be demineralized again before reactor reuse.

Existing machine saves migrate only the affected machine tanks: legacy water becomes the machine's required fresh or light water, and legacy PWR coolant becomes borated water. Standalone tanks, pipes, barrels, and held containers are not converted.
