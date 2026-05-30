// Compare 3 shrink levels, each clipped to a circle (simulating Nothing icon pack).
// Goal: gear fully inside, but icon not too small.
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

// label panels for 0.72 / 0.80 / 0.88
const panel = (x, s, label) => `
  <g transform="translate(${x},0)">
    <g clip-path="url(#c)">
      <rect width="108" height="108" fill="${BG}"/>
      <g transform="translate(54,54) scale(${s}) translate(-54,-54)">${art}</g>
    </g>
    <text x="54" y="124" font-family="Arial" font-size="11" font-weight="bold"
          text-anchor="middle" fill="#333">scale ${label}</text>
  </g>`;

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1040" height="540" viewBox="0 0 380 132">
  <defs><clipPath id="c"><circle cx="54" cy="54" r="54"/></clipPath></defs>
  <rect width="380" height="132" fill="#FFFFFF"/>
  ${panel(0, 0.72, '0.72')}
  ${panel(136, 0.80, '0.80')}
  ${panel(272, 0.88, '0.88 (gear may clip)')}
</svg>`;

const png = new Resvg(svg, { fitTo: { mode: 'width', value: 1040 } }).render().asPng();
writeFileSync(new URL('./_icon-scales.png', import.meta.url), png);
console.log('Rendered _icon-scales.png  (0.72 / 0.80 / 0.88)');
