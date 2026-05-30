// Render BEFORE (current) vs AFTER (shrunk 0.68) launcher foreground, each
// clipped to a circle mask, to verify the gear is no longer clipped.
import { Resvg } from '../index/composer-2.5/icons/node_modules/@resvg/resvg-js/index.js';
import { writeFileSync } from 'node:fs';

const BG = '#06C755';
const bubble = 'M12,3C7.03,3 3,6.13 3,10c0,2.28 1.46,4.3 3.68,5.57L6,19.5c-0.12,0.42 0.25,0.64 0.58,0.42l3.52,-2.1c0.6,0.1 1.24,0.18 1.9,0.18c4.97,0 9,-3.13 9,-7s-4.03,-7 -9,-7z';
const plus = 'M11.25,7.5v2.25H9v1.5h2.25V13.5h1.5V11.25H15v-1.5h-2.25V7.5z';
const gear = 'M6,0.8l0.6,0 0.2,0.9c0.25,0.1 0.48,0.23 0.69,0.38l0.82,-0.35 0.56,0.98 -0.65,0.56c0.04,0.15 0.06,0.3 0.06,0.46s-0.02,0.31 -0.06,0.46l0.65,0.56 -0.56,0.98 -0.82,-0.35c-0.21,0.15 -0.44,0.28 -0.69,0.38L6.6,6.6H5.4L5.2,5.73c-0.25,-0.1 -0.48,-0.23 -0.69,-0.38l-0.82,0.35 -0.56,-0.98 0.65,-0.56C3.74,4.01 3.72,3.86 3.72,3.7s0.02,-0.31 0.06,-0.46l-0.65,-0.56 0.56,-0.98 0.82,0.35c0.21,-0.15 0.44,-0.28 0.69,-0.38L5.4,0.8H6zM6,2.6a1.1,1.1 0,1 0,0 2.2a1.1,1.1 0,1 0,0 -2.2z';

const art = `
  <g transform="translate(19,23) scale(2.94)">
    <path fill="#FFFFFF" d="${bubble}"/>
    <path fill="${BG}" d="${plus}"/>
  </g>
  <g transform="translate(63,17) scale(2.42)">
    <path fill="#FFFFFF" d="${gear}"/>
  </g>`;

// viewBox: two 108 icons + 16 gap = 232 wide. Circle masks simulate launcher.
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="928" height="432" viewBox="0 0 232 108">
  <defs>
    <clipPath id="cL"><circle cx="54" cy="54" r="54"/></clipPath>
    <clipPath id="cR"><circle cx="54" cy="54" r="54"/></clipPath>
  </defs>

  <!-- BEFORE: current foreground, gear clipped by circle -->
  <g clip-path="url(#cL)">
    <rect x="0" y="0" width="108" height="108" fill="${BG}"/>
    ${art}
  </g>

  <!-- AFTER: same art shrunk to 0.68 around center, in right panel -->
  <g transform="translate(124,0)">
    <g clip-path="url(#cR)">
      <rect x="0" y="0" width="108" height="108" fill="${BG}"/>
      <g transform="translate(54,54) scale(0.68) translate(-54,-54)">
        ${art}
      </g>
    </g>
  </g>
</svg>`;

const png = new Resvg(svg, { fitTo: { mode: 'width', value: 928 } }).render().asPng();
writeFileSync(new URL('./_icon-fix-preview.png', import.meta.url), png);
console.log('Rendered _icon-fix-preview.png  (LEFT=current clipped, RIGHT=fixed 0.68)');
