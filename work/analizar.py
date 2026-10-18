from pathlib import Path
import subprocess, sys, re, json
from PIL import Image, ImageDraw, ImageOps
sys.path.insert(0, str(Path(__file__).parent / 'python'))
import imageio_ffmpeg
base = Path(__file__).parent
src = base / 'testprueba1'
ff = imageio_ffmpeg.get_ffmpeg_exe()
ref = next(src.glob('*.mp4'))
info = subprocess.run([ff, '-hide_banner', '-i', str(ref)], capture_output=True, text=True).stderr
print(info)
out = base / 'analisis'
out.mkdir(exist_ok=True)
subprocess.run([ff, '-v', 'error', '-i', str(ref), '-vf', 'fps=4,scale=216:-1', str(out / 'frame-%04d.jpg')], check=True)
frames = sorted(out.glob('frame-*.jpg'))
def sheet(items, name, cols, w, h, label):
    rows = (len(items)+cols-1)//cols
    canvas=Image.new('RGB',(cols*w,rows*h),'#171717')
    draw=ImageDraw.Draw(canvas)
    for i,p in enumerate(items):
        im=Image.open(p).convert('RGB')
        thumb=ImageOps.contain(im,(w-10,h-30))
        x=(i%cols)*w+(w-thumb.width)//2
        y=(i//cols)*h
        canvas.paste(thumb,(x,y))
        draw.text(((i%cols)*w+4,y+h-25),label(i,p),fill='white')
    canvas.save(out/name)
for part in range(0,len(frames),40):
    sheet(frames[part:part+40],f'referencia-{part//40+1}.jpg',8,180,300,lambda i,p:f'{(part+i)/4:.2f}s')
photos=sorted(src.glob('*.jpg'))
sheet(photos,'fotos.jpg',4,260,430,lambda i,p:f'{i+1}: {p.stem[-13:]}')
print(json.dumps({'frames':len(frames),'photos':[{'name':p.name,'size':Image.open(p).size} for p in photos]},indent=2))
