"""Build visual QA from actual renderer captures, without editing game sprites."""
from pathlib import Path
from PIL import Image, ImageDraw
import json

HERE = Path(__file__).resolve().parent
CAP = HERE.parents[1] / 'build/character-captures'
NPCS = ['baker','redhood','child','elder','maid','boy']

for state in ['free','trapped']:
    sheet = Image.new('RGB',(1280,6*270),'#151a24')
    draw = ImageDraw.Draw(sheet)
    for row,npc in enumerate(NPCS):
        for col,version in enumerate(['before','after']):
            im=Image.open(CAP/version/f'{state}-{npc}.png').crop((70,40,570,290))
            sheet.paste(im,(col*640+70,row*270+20))
            draw.text((col*640+10,row*270+5),f'{npc} / {version}',fill='white')
    sheet.save(HERE/f'npc-{state}-comparison.jpg',quality=94)

sequences = ['walk-'+d for d in ['down','up','right','left']]+['roll-'+d for d in ['down','up','right','left']]+['attack','skill','mining','cheer-baker']
for sequence in ['walk-right','roll-right','roll-down','attack','cheer-baker']:
    frames=[]
    for frame in range(0,96,2):
        im=Image.new('RGB',(800,382),'#151a24');d=ImageDraw.Draw(im)
        for col,version in enumerate(['before','after']):
            src=Image.open(CAP/version/sequence/f'{frame:03d}.png').crop((120,0,520,360))
            im.paste(src,(col*400,22));d.text((col*400+10,6),f'{sequence} / {version}',fill='white')
        frames.append(im)
    palette=frames[0].quantize(colors=192,method=Image.Quantize.MEDIANCUT)
    frames=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
    frames[0].save(HERE/f'{sequence}.gif',save_all=True,append_images=frames[1:],duration=[30,30,40]*16,loop=0,optimize=True)

# Inspect every tumble direction and the transitions, including mirrored left/up.
sheet=Image.new('RGB',(1600,4*205),'#151a24');draw=ImageDraw.Draw(sheet)
for row,direction in enumerate(['down','up','right','left']):
    for col,frame in enumerate([12,14,16,20,22,26,30,34]):
        im=Image.open(CAP/'after'/f'roll-{direction}'/f'{frame:03d}.png').resize((200,112))
        sheet.paste(im,(col*200,row*205+30));draw.text((col*200+5,row*205+10),f'{direction} {frame}/60s',fill='white')
sheet.save(HERE/'roll-directions.jpg',quality=95)

counts={}
for version in ['before','after']:
    count=len(list((CAP/version).glob('*.png')))
    assert count==12,(version,count)
    for sequence in sequences:
        files=list((CAP/version/sequence).glob('*.png'))
        assert len(files)==48,(version,sequence,len(files))
        count+=len(files)
    counts[version]=count
(HERE/'capture-report.json').write_text(json.dumps({'simulationFps':60,'captureFps':30,'size':[640,360],'counts':counts,'sequences':sequences},indent=2),encoding='utf-8')
print('Verified',counts,'and composed actual-renderer comparisons.')
