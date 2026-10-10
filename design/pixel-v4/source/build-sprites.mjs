// Editable pixel source. No bitmap inputs, resampling, quantization or AI output
// are used here. Each character below is one original game-art pixel.
// Emits the same palette/sprite text grammar used by gfx/Art.java.
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const palette = {
  '.':'00000000', k:'20212fff', H:'49332fff', h:'805139ff', l:'bb8450ff',
  S:'bb7958ff', s:'e6aa77ff', f:'f5ce99ff',
  R:'642b39ff', r:'aa3747ff', t:'e15b54ff',
  B:'203449ff', b:'34516bff', c:'56768bff',
  C:'b4a487ff', w:'eee0b9ff',
  G:'4d3832ff', g:'80593fff', L:'b58a58ff',
  P:'34343fff', p:'54505aff',
  Y:'987344ff', y:'e0b363ff',
  a:'878b8dff', d:'c4c7beff', i:'fff1ceff'
};
// 32 x 48; front idle; foot anchor at (15, 45), coordinates from top-left.
// Short rows contain transparent padding on their right.
const hero = [
  '', '', '',
  '..............kk',
  '............kkhhkk',
  '.........kkkhhllhhkk',
  '.......kkhhhlllllhhhkk',
  '......khhhlllllhhhhhHkk',
  '.....khhhlllhhhhhhhhHHHk',
  '......khlllhhhhhhhhhHHHHk',
  '.....khhhhhhllhhhhHHHHHHkk',
  '......kHhhhhhhhhhHHHhHHHk',
  '.....kHhhhhhhhhHHHhhHHHHk',
  '......kHHhhhHHHffHHhHHHk',
  '......kHHhhHHfffffHHHHHk',
  '......kSHHkfkkfffkfkkHSk',
  '......kSsSfkwkfffkwkSsSk',
  '.......kSsfkkfffffkkfsSk',
  '........kSfffffffffsSk',
  '.........kSfffssfffSk',
  '........kRRSSffffSRRkk',
  '.......kRrtttrrrrtttrRkkkk',
  '......kBbRrttttttttrRRrtttrkk',
  '.....kBbbBRrrrrrrrRRBkRrrtttrrk',
  '.....kBcbbBRrrrrrRRbbBk.kRrtrRk',
  '....kBccbbBgRRRRRbbbBBk..kRRrRk',
  '....kCwCbBggLgbbbbbbBCwk...kRk',
  '....kwwCCBgLggLbbbbbCwwCk',
  '.....kfsSkBgLggLbbbBkSsfk',
  '.....kSsSkBbgLggLbbBkSsSk',
  '....kPppPkBGggLggLBGkPppPk',
  '....kPppPkGggggyyggGkPppPk',
  '....kPppPkGggggYyggGkPppPk',
  '.....kSskBbbbbkbbbbbBkSsk',
  '......kkBbcbbwkWbbbbBkk',
  '........kbcbbwkwbbbbBk',
  '........kCwwwkkwwwCCk',
  '.........kPppk.kPppk',
  '.........kPppk.kPppk',
  '.........kGLLk.kGLLk',
  '.........kgLgk.kgLgk',
  '.........kGggk.kGggk',
  '........kgLggk.kggLgk',
  '........kggGGk.kGGggk',
  '........kkkkkk.kkkkkk',
  '', '', ''
].map(row => row.replaceAll('W', 'C').padEnd(32, '.'));

// Diagonal sword. Integer-grid silhouette, two seals, single-plane highlights.
// Coordinate-space drawing here authors new pixels; it never reads a bitmap.
const sword = Array.from({length:32}, () => Array(32).fill('.'));
const dot = (x,y,c) => { if(x<0||x>=32||y<0||y>=32) throw Error('Pixel outside frame'); sword[y][x]=c; };
// A 45-degree blade from (11,21) to (28,3), 5-6 pixels wide.
for (let y=3;y<=22;y++) {
  const center=31-y;
  const half=y<6 ? Math.floor((y-3)/2)+1 : 3;
  for(let x=center-half;x<=center+half;x++) {
    if(x>29) continue;
    const edge=x===center-half||x===center+half;
    dot(x,y,edge?'k':x<center?'i':x===center?'w':'d');
  }
}
// Lower-right shadow: keep one connected plane, not sparkle noise.
for(let y=7;y<=20;y++) dot(33-y,y,'a');
for(const band of [12,23]) {
  // Each seal crosses perpendicular to blade, using integer diagonals.
  for(let y=3;y<24;y++) for(let x=8;x<30;x++) {
    if(sword[y][x]!=='.' && (y-x===band-24 || y-x===band-23))
      sword[y][x]=(y-x===band-24)?'g':'G';
  }
}
// Grip lower-left, behind guard.
for(let n=0;n<7;n++) for(let o=-1;o<=1;o++) dot(9-n+o,23+n,o===-1?'g':o===0?'L':'G');
// Guard runs perpendicular to blade; dark edges then simple brass face.
for(let n=0;n<9;n++) for(let o=-1;o<=1;o++) dot(7+n,18+n+o,o===0?'y':'Y');
dot(7,17,'k');dot(6,18,'k');dot(15,27,'k');dot(16,26,'k');
dot(11,22,'y');dot(11,23,'Y');
// Pommel and opaque grip pivot at (6,26).
for(let y=27;y<=29;y++) for(let x=2;x<=4;x++) dot(x,y,y===28?'y':'Y');
dot(6,26,'g');

const sprites = [
  {name:'hero_down',width:32,height:48,rows:hero,origin:[15,45],hand:[23,31]},
  {name:'sword_holy',width:32,height:32,rows:sword.map(row=>row.join('')),origin:[6,26],angle:45}
];
for(const sprite of sprites) {
  if(sprite.rows.length!==sprite.height) throw Error(`${sprite.name}: wrong row count ${sprite.rows.length}`);
  for(const row of sprite.rows) {
    if(row.length!==sprite.width) throw Error(`${sprite.name}: wrong row width ${row.length}: ${row}`);
    for(const char of row) if(!(char in palette)) throw Error(`Undefined palette symbol ${char}`);
  }
}
mkdirSync(join(root,'native'),{recursive:true});
const text = ['# Original pixel-grid study. Not installed into runtime assets.',
  '# RGBA colors; a character is exactly one pixel; no partial alpha.',
  ...Object.entries(palette).map(([key,rgba])=>`palette ${key} ${rgba}`), '',
  ...sprites.flatMap(s=>[`sprite ${s.name} ${s.width} ${s.height}`,...s.rows,''])].join('\n');
writeFileSync(join(root,'native','sprites.txt'),text);
writeFileSync(join(root,'native','sprites.json'),JSON.stringify({palette,sprites},null,2)+'\n');
// The gallery works from file:// too; no fetch or web service is needed.
writeFileSync(join(root,'native','sprites.js'),'window.NATIVE_SPRITES = '+JSON.stringify({palette,sprites})+';\n');
console.log('Validated and wrote 2 original grid sprites.');
