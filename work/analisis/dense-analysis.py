from pathlib import Path
import subprocess
from PIL import Image, ImageDraw, ImageOps
base=Path(r'C:\Users\PC\Downloads\royer-method-work')
ff=base/'python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
ref=base/'testprueba1/675cab0eadd1705a9794a2f651390bed.mp4'
out=base/'analisis/denso'
out.mkdir(exist_ok=True)
subprocess.run([str(ff),'-y','-v','error','-ss','4.700000','-i',str(ref),'-t','9.2','-vf','scale=192:-1','-fps_mode','passthrough',str(out/'dense-%04d.jpg')],check=True)
frames=sorted(out.glob('dense-*.jpg'))
for start in range(0,len(frames),30):
    page=frames[start:start+30]
    canvas=Image.new('RGB',(10*192,3*282),'#151515')
    d=ImageDraw.Draw(canvas)
    for i,p in enumerate(page):
        im=Image.open(p)
        x=(i%10)*192;y=(i//10)*282
        canvas.paste(im,(x,y))
        d.text((x+5,y+260),f'{4.7+(start+i)/30:.3f}s',fill='white')
    canvas.save(out/f'dense-sheet-{start//30+1:02d}.jpg',quality=92)
print(len(frames))