"""Comparison sheets from actual GL screenshots. Does not edit game artwork."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parent
font=ImageFont.truetype('C:/Windows/Fonts/malgun.ttf',22)
small=ImageFont.truetype('C:/Windows/Fonts/malgun.ttf',16)
for name in ['north','side']:
    board=Image.new('RGB',(1280,650),'#10141e');d=ImageDraw.Draw(board)
    for col,variant in enumerate(['A','B']):
        im=Image.open(root/'captures'/f'{variant}-mine-{name}.png').crop((320,100,960,680))
        board.paste(im,(640*col,70))
        label='A · 현재 버전' if variant=='A' else 'B · 방 경계 표식 추가'
        description='열린 길과 계단으로 자연스럽게 연결' if variant=='A' else '양옆 표식 + 바닥 문턱으로 입장 지점 구분'
        d.text((640*col+16,9),label,font=font,fill='white')
        d.text((640*col+16,41),description,font=small,fill='#b6c2d1')
    board.save(root/f'{name}-ab.jpg',quality=94)
