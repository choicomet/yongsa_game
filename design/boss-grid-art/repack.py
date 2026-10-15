"""Repack generated fixed-grid bosses under the user's 2026-10-15 resize approval.
No painting/recoloring. Preserve originals, choose reviewed row, use one uniform
scale per atlas and translate each cell to the original ground baseline.
"""
from pathlib import Path
from PIL import Image
import statistics, json, hashlib

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).parent
report=[]
for name in ('boss_golem','boss_knight','boss_lich'):
    src=Image.open(HERE/'sources'/f'{name}.png').convert('RGBA')
    ref=Image.open(HERE/'reference'/f'{name}.png').convert('RGBA')
    repaired=Image.open(HERE/'sources'/'boss_lich-special.png').convert('RGBA') if name=='boss_lich' else None
    def cell(im,row,col):
        alpha=im.getchannel('A')
        def cut(nominal,extent,step,horizontal,lo=0,hi=None):
            if nominal in (0,extent):
                return nominal
            hi=hi or (im.width if horizontal else im.height)
            candidates=range(max(1,nominal-round(step*.12)),min(extent,nominal+round(step*.12))+1)
            def cost(k):
                rect=(lo,k,hi,k+1) if horizontal else (k,lo,k+1,hi)
                return (alpha.crop(rect).getextrema()[1],abs(k-nominal))
            return min(candidates,key=cost)
        y0=cut(round(row*im.height/5),im.height,im.height/5,True)
        y1=cut(round((row+1)*im.height/5),im.height,im.height/5,True)
        x0=cut(round(col*im.width/8),im.width,im.width/8,False,y0,y1)
        x1=cut(round((col+1)*im.width/8),im.width,im.width/8,False,y0,y1)
        frame=im.crop((x0,y0,x1,y1))
        frame.info['anchor_x']=(col+.5)*im.width/8-x0
        frame.info['source_rect']=[x0,y0,x1,y1]
        return frame
    def visible_box(im):
        return im.getchannel('A').point(lambda a:255 if a>8 else 0).getbbox()
    heights=[visible_box(cell(src,0,c))[3]-visible_box(cell(src,0,c))[1] for c in range(4)]
    scale=128/statistics.median(heights)
    out=Image.new('RGBA',(2048,1280))
    frames=[]
    for row in range(5):
        for col in range(8):
            chosen=repaired if repaired is not None and row==3 else src
            frame=cell(chosen,row,col)
            box=visible_box(frame)
            assert box, (name,row,col,'empty source')
            # Refuse a cut through clearly visible art. Faint edge antialias is also recorded.
            edges=[frame.crop((0,0,1,frame.height)),frame.crop((frame.width-1,0,frame.width,frame.height)),
                   frame.crop((0,0,frame.width,1)),frame.crop((0,frame.height-1,frame.width,frame.height))]
            edge_max=max(e.getchannel('A').getextrema()[1] for e in edges)
            assert edge_max<=8,(name,row,col,'source crosses boundary',edge_max)
            resized=frame.resize((round(frame.width*scale),round(frame.height*scale)),Image.Resampling.NEAREST)
            new_box=visible_box(resized)
            # Grid center is the horizontal reference; bbox center moves with swings.
            dx=128-round(frame.info['anchor_x']*scale)
            # Existing reference retains jumping/collapse trajectories. Ground is pixel224.
            rb=visible_box(ref.crop((col*256,row*256,(col+1)*256,(row+1)*256)))
            bottom=rb[3] if row else 224
            dy=bottom-new_box[3]
            assert 0 <= dx and dx+resized.width<=256
            assert dy+new_box[1]>=0 and dy+new_box[3]<=256
            # Trim only the near-transparent exterior (alpha <= 8) with a 2px halo.
            # Pasting the entire generated cell would carry noise into the next row.
            trim=(max(0,new_box[0]-2),max(0,new_box[1]-2),
                  min(resized.width,new_box[2]+2),min(resized.height,new_box[3]+2))
            out.alpha_composite(resized.crop(trim),(col*256+dx+trim[0],row*256+dy+trim[1]))
            frames.append(dict(row=row,column=col,sourceBox=box,sourceRect=frame.info['source_rect'],sourceEdgeAlpha=edge_max,
                               visibleBox=[dx+new_box[0],dy+new_box[1],dx+new_box[2],dy+new_box[3]],
                               source='special-repair' if chosen is repaired else 'initial',
                               translation=[dx,dy]))
    dest=ROOT/'assets/team/jiyong'/f'{name}.png'
    out.save(dest)
    assert out.size==(2048,1280)
    for row in range(5):
        for col in range(8):
            f=out.crop((col*256,row*256,(col+1)*256,(row+1)*256))
            b=f.getchannel('A').getbbox()
            assert b and b[0]>0 and b[1]>0 and b[2]<256 and b[3]<256,(name,row,col,b)
    report.append(dict(file=dest.relative_to(ROOT).as_posix(),sourceSize=src.size,scale=scale,
                       targetSize=out.size,frames=frames,sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
(HERE/'inspection.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('PASS: 3 sheets, 120 nonempty frames, transparent cell margins, exact 2048x1280.')
